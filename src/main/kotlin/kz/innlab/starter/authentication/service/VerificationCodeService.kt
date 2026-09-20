package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.IssuedCode
import kz.innlab.starter.authentication.model.VerificationCode
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.config.VerificationProperties
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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
    private val verificationProperties: VerificationProperties
) {

    companion object {
        private const val DEFAULT_EXPIRY_MINUTES = 15L

        /** Phone OTPs are short-lived: they are read off a screen and typed in immediately. */
        private const val PHONE_EXPIRY_MINUTES = 5L

        private const val RATE_LIMIT_SECONDS = 60L
        private const val MAX_ATTEMPTS = 3
    }

    @Transactional
    fun createCode(
        identifier: String,
        purpose: VerificationPurpose,
        newValue: String? = null,
        userId: UUID? = null
    ): IssuedCode {
        val now = Instant.now()

        // Rate limit: at most one code per identifier+purpose per minute
        if (verificationCodeRepository.existsByIdentifierAndPurposeAndCreatedAtAfter(
                identifier, purpose, now.minusSeconds(RATE_LIMIT_SECONDS)
            )
        ) {
            throw IllegalStateException("Please wait before requesting a new code")
        }

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
        identifier: String,
        purpose: VerificationPurpose,
        code: String
    ): VerificationCode {
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
        attemptRecorder.recordAttempt(record.id)

        if (!passwordEncoder.matches(code, record.codeHash)) {
            throw BadCredentialsException("Invalid verification code")
        }

        // Burn the code independently too: if the caller's transaction later rolls back, a
        // one-time code must not become reusable.
        attemptRecorder.markUsed(record.id)

        return record
    }

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
