package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.IssuedCode
import kz.innlab.starter.authentication.model.VerificationCode
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.config.RateLimitProperties
import kz.innlab.starter.config.VerificationProperties
import kz.innlab.starter.shared.ratelimit.RateLimitExceededException
import kz.innlab.starter.shared.ratelimit.RateLimiter
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.UUID

/**
 * Single owner of one-time verification codes for every channel and purpose.
 *
 * Phone OTP used to live in a separate service and table with its own copy of the
 * rate limit, attempt counter and expiry checks. The copies drifted — the Telegram
 * flow ended up with no resend cooldown at all — so the brute-force protection now
 * exists exactly once, here.
 */
@Service
class VerificationCodeService(
    private val verificationCodeRepository: VerificationCodeRepository,
    private val attemptRecorder: VerificationAttemptRecorder,
    private val passwordEncoder: PasswordEncoder,
    private val verificationProperties: VerificationProperties,
    private val rateLimiter: RateLimiter,
    private val rateLimitProperties: RateLimitProperties
) {

    companion object {
        private const val DEFAULT_EXPIRY_MINUTES = 15L

        /** Phone OTPs are short-lived: they are read off a screen and typed in immediately. */
        const val PHONE_EXPIRY_MINUTES = 5L

        private const val RATE_LIMIT_SECONDS = 60L
        private const val MAX_ATTEMPTS = 3

        /** Redraws allowed for a reissued code that equals the old one (each repeat is 10^-length). */
        private const val REISSUE_DRAWS = 10

        /** Purposes whose identifier is an email address. */
        private val EMAIL_PURPOSES = setOf(
            VerificationPurpose.FORGOT_PASSWORD,
            VerificationPurpose.VERIFY_EMAIL,
            VerificationPurpose.EMAIL_LOGIN
        )
    }

    @Transactional
    fun createCode(
        rawIdentifier: String,
        purpose: VerificationPurpose,
        newValue: String? = null,
        userId: UUID? = null
    ): IssuedCode {
        val identifier = normalizeIdentifier(rawIdentifier, purpose)
        val now = Instant.now()

        // Rate limit: at most one code per identifier+purpose per minute
        if (verificationCodeRepository.existsByIdentifierAndPurposeAndCreatedAtAfter(
                identifier, purpose, now.minusSeconds(RATE_LIMIT_SECONDS)
            )
        ) {
            throw IllegalStateException("Please wait before requesting a new code")
        }
        checkSendAllowed(purpose)

        val code = OneTimeCodes.generate(devCodeFor(purpose), codeLengthFor(purpose))
        val hash = requireNotNull(passwordEncoder.encode(code)) { "PasswordEncoder returned null hash" }

        // Only one live code per identifier+purpose
        verificationCodeRepository.deleteAllByIdentifierAndPurpose(identifier, purpose)

        val saved = verificationCodeRepository.save(
            VerificationCode(
                identifier = identifier,
                purpose = purpose,
                codeHash = hash,
                expiresAt = now.plusSeconds(expiryMinutesFor(purpose) * 60),
                newValue = newValue,
                userId = userId
            )
        )

        return IssuedCode(
            verificationId = saved.id,
            code = code,
            resendAvailableAt = now.plusSeconds(RATE_LIMIT_SECONDS),
            retryAfterSeconds = RATE_LIMIT_SECONDS
        )
    }

    @Transactional
    fun verifyCode(
        verificationId: UUID,
        rawIdentifier: String,
        purpose: VerificationPurpose,
        code: String
    ): VerificationCode {
        val identifier = normalizeIdentifier(rawIdentifier, purpose)
        // Budget across codes: requesting a new code resets the per-code attempt counter below,
        // so on its own that counter allows unlimited guessing at one code request a minute.
        val budgetKey = "otp-verify:$purpose:$identifier"
        val rule = rateLimitProperties.otpVerify
        if (rateLimitProperties.enabled && !rateLimiter.tryAcquire(budgetKey, rule.maxAttempts, rule.windowSeconds)) {
            throw RateLimitExceededException("Too many attempts. Try again later.", rule.windowSeconds)
        }

        val record = verificationCodeRepository.findById(verificationId).orElseThrow {
            BadCredentialsException("Invalid verification code")
        }

        // Identifier and purpose must match the issued code: prevents guessing a verificationId
        // and redeeming it against a different phone/email or a different flow.
        if (record.identifier != identifier) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.purpose != purpose) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.used) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.expiresAt <= Instant.now()) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.attempts >= MAX_ATTEMPTS) {
            throw BadCredentialsException("Invalid verification code")
        }

        // Count the attempt before checking the code, in its own transaction: throwing below
        // rolls the caller's transaction back, and an increment made here would go with it —
        // which is exactly why the limit never bit before.
        if (!attemptRecorder.recordAttempt(record.id, MAX_ATTEMPTS)) {
            throw BadCredentialsException("Invalid verification code")
        }

        if (!passwordEncoder.matches(code, record.codeHash)) {
            throw BadCredentialsException("Invalid verification code")
        }

        // Burn the code independently too: if the caller's transaction later rolls back, a
        // one-time code must not become reusable.
        // Against the hash just checked: if reissueCode replaced the code meanwhile, the old one
        // must not be redeemed.
        if (!attemptRecorder.markUsed(record.id, record.codeHash, MAX_ATTEMPTS)) {
            throw BadCredentialsException("Invalid verification code")
        }

        rateLimiter.reset(budgetKey)
        return record
    }

    /**
     * Replaces the code of a verification whose message the Telegram Gateway could not deliver
     * (request [gatewayRequestId]) with a new one, for delivery through another channel.
     *
     * The record keeps its id — the client already holds it as `verificationId` — as well as its
     * expiry and attempt count: a reissue grants neither more time nor more guesses. The old code
     * stops working because its hash is replaced. The send counts against the per-purpose limit;
     * not against the per-client one, since the HTTP client here is the Gateway, not the user.
     *
     * Returns null, changing nothing, when the verification is unknown, used, expired, out of
     * attempts, or no longer carried by that request (already reissued). Throws
     * RateLimitExceededException when the send limit is spent.
     */
    @Transactional
    fun reissueCode(verificationId: UUID, gatewayRequestId: String): IssuedCode? {
        val record = verificationCodeRepository.findById(verificationId).orElse(null) ?: return null
        val now = Instant.now()
        if (record.gatewayRequestId != gatewayRequestId || record.used ||
            record.expiresAt <= now || record.attempts >= MAX_ATTEMPTS
        ) {
            return null
        }
        checkSendAllowed(record.purpose, client = null)

        val code = newCodeUnlike(record.codeHash, record.purpose)
        val hash = requireNotNull(passwordEncoder.encode(code)) { "PasswordEncoder returned null hash" }
        if (verificationCodeRepository.replaceCodeIfActive(record.id, gatewayRequestId, hash, now, MAX_ATTEMPTS) != 1) {
            return null
        }
        return IssuedCode(
            verificationId = record.id,
            code = code,
            resendAvailableAt = now.plusSeconds(RATE_LIMIT_SECONDS),
            retryAfterSeconds = RATE_LIMIT_SECONDS
        )
    }

    /**
     * A code that does not match [oldHash]. The dev override is used only when it differs from the
     * old code — with a fixed dev code it never does, and a "new" code equal to the undelivered one
     * would leave the old code working. A random code repeats the old one with probability 10^-length;
     * that is redrawn too.
     */
    private fun newCodeUnlike(oldHash: String, purpose: VerificationPurpose): String {
        val length = codeLengthFor(purpose)
        val devCode = devCodeFor(purpose)
        if (devCode.isNotBlank() && !passwordEncoder.matches(devCode, oldHash)) return devCode
        repeat(REISSUE_DRAWS) {
            val code = OneTimeCodes.generate("", length)
            if (!passwordEncoder.matches(code, oldHash)) return code
        }
        error("could not draw a code different from the previous one")
    }

    /**
     * Counts one outgoing code against the per-client and per-purpose send limits; throws 429 once
     * either is spent. [createCode] calls it; a flow that answers as if it sent a code without
     * sending one (to hide whether an account exists) must call it too, or the limit itself
     * would tell the two cases apart.
     */
    fun checkSendAllowed(purpose: VerificationPurpose) = checkSendAllowed(purpose, currentClientAddress())

    private fun checkSendAllowed(purpose: VerificationPurpose, client: String?) {
        if (!rateLimitProperties.enabled) return
        val perClient = rateLimitProperties.codeSendPerClient
        if (client != null &&
            !rateLimiter.tryAcquire("code-send:client:$client", perClient.maxAttempts, perClient.windowSeconds)
        ) {
            throw RateLimitExceededException("Too many codes requested. Try again later.", perClient.windowSeconds)
        }
        val perPurpose = rateLimitProperties.codeSendPerPurpose
        if (!rateLimiter.tryAcquire("code-send:purpose:$purpose", perPurpose.maxAttempts, perPurpose.windowSeconds)) {
            throw RateLimitExceededException("Too many codes requested. Try again later.", perPurpose.windowSeconds)
        }
    }

    // Null outside an HTTP request (a scheduled job, a test calling the service directly).
    private fun currentClientAddress(): String? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.remoteAddr
            ?.let(::clientBucket)

    /**
     * IPv6 clients are bucketed by /64: one subscriber normally gets a whole /64, so keying on the
     * full address let a client use a fresh address per request and never hit the limit.
     * IPv4 addresses are used as they are.
     */
    internal fun clientBucket(address: String): String {
        val parsed = runCatching { InetAddress.ofLiteral(address) }.getOrNull() ?: return address
        if (parsed !is Inet6Address) return address
        return parsed.address.copyOf(8).joinToString(":", postfix = "::/64") { "%02x".format(it) }
    }

    /**
     * Email identifiers are compared without case: the cooldown, the guessing budget and the code
     * row are all keyed on the identifier, and keying them on the raw input gave every case
     * variant of an address (VICTIM@x, Victim@x, ...) its own code and its own budget — unlimited
     * guessing at one request a minute. Other identifiers (E.164 phones, user ids) are exact.
     */
    private fun normalizeIdentifier(identifier: String, purpose: VerificationPurpose): String =
        if (purpose in EMAIL_PURPOSES) identifier.trim().lowercase(Locale.ROOT) else identifier

    private fun expiryMinutesFor(purpose: VerificationPurpose): Long =
        if (purpose == VerificationPurpose.PHONE_LOGIN || purpose == VerificationPurpose.EMAIL_LOGIN) {
            PHONE_EXPIRY_MINUTES
        } else {
            DEFAULT_EXPIRY_MINUTES
        }

    private fun codeLengthFor(purpose: VerificationPurpose): Int = when (purpose) {
        VerificationPurpose.PHONE_LOGIN -> verificationProperties.phone.codeLength
        VerificationPurpose.EMAIL_LOGIN -> verificationProperties.emailOtp.codeLength
        else -> 6
    }

    // Phone OTP keeps its own dev override so app.auth.sms.dev-code stays meaningful.
    private fun devCodeFor(purpose: VerificationPurpose): String =
        when (purpose) {
            VerificationPurpose.PHONE_LOGIN -> verificationProperties.sms.devCode
            VerificationPurpose.EMAIL_LOGIN -> verificationProperties.emailOtp.devCode
            else -> verificationProperties.verification.devCode
        }
}
