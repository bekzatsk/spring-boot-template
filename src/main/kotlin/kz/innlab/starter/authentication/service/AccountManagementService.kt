package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.ReauthCodeResponse
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.config.AsyncConfig
import kz.innlab.starter.config.RateLimitProperties
import kz.innlab.starter.shared.error.ForbiddenOperationException
import kz.innlab.starter.shared.error.ResourceNotFoundException
import kz.innlab.starter.shared.ratelimit.RateLimitExceededException
import kz.innlab.starter.shared.ratelimit.RateLimiter
import kz.innlab.starter.shared.util.normalizeToE164
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

@Service
class AccountManagementService(
    private val userRepository: UserRepository,
    private val verificationCodeService: VerificationCodeService,
    private val emailService: EmailService,
    private val otpDeliveryService: OtpDeliveryService,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val rateLimiter: RateLimiter,
    private val rateLimitProperties: RateLimitProperties,
    // Qualified here as well as at the @Bean method: a scan-built instance would otherwise see
    // two Executor beans (this one and Spring's taskScheduler).
    @param:Qualifier(AsyncConfig.STARTER_EXECUTOR) private val codeSendExecutor: Executor
) {

    /**
     * Request password reset (unauthenticated).
     *
     * Answers the same way whether or not the address has a password account: a verificationId
     * (a random one when there is nothing to reset), the same cooldown, and roughly the same time.
     * The code is sent off the request thread, so mail latency does not tell the two apart either.
     */
    fun requestPasswordReset(email: String): UUID {
        enforceCodeRequestCooldown(email, VerificationPurpose.FORGOT_PASSWORD)
        val user = userRepository.findByEmail(email)

        // Social-only users have no password to reset
        if (user == null || AuthProvider.LOCAL !in user.providers || user.passwordHash == null) {
            return decoyVerificationId()
        }

        val (verificationId, code) = verificationCodeService.createCode(email, VerificationPurpose.FORGOT_PASSWORD)
        codeSendExecutor.execute { emailService.sendCode(email, code, "FORGOT_PASSWORD") }
        return verificationId
    }

    /**
     * Reset password using verification code (unauthenticated).
     * Revokes all refresh tokens after reset (security: password was compromised).
     */
    @Transactional
    fun resetPassword(verificationId: UUID, email: String, code: String, newPassword: String) {
        verificationCodeService.verifyCode(verificationId, email, VerificationPurpose.FORGOT_PASSWORD, code)

        val user = userRepository.findByEmail(email)
            ?: throw BadCredentialsException("Invalid verification code")

        user.passwordHash = passwordEncoder.encode(newPassword)
        user.passwordTemporary = false
        user.requiredActions.remove(RequiredAction.UPDATE_PASSWORD)
        userRepository.save(user)
        refreshTokenRepository.deleteAllByUser(user)
    }

    /**
     * Verify email ownership with a code (unauthenticated).
     * Clears the VERIFY_EMAIL required action so the user gains full access on next token issuance.
     *
     * Not idempotent on purpose: answering 200 for an already-verified address without checking
     * the code told anyone which addresses have verified accounts. A repeat call fails like a
     * wrong code, because the code is spent.
     */
    @Transactional
    fun verifyEmail(email: String, verificationId: UUID, code: String) {
        verificationCodeService.verifyCode(verificationId, email, VerificationPurpose.VERIFY_EMAIL, code)

        val user = userRepository.findByEmail(email)
            ?: throw BadCredentialsException("Invalid verification code")

        user.emailVerified = true
        user.requiredActions.remove(RequiredAction.VERIFY_EMAIL)
        userRepository.save(user)
    }

    /**
     * Resend the email-verification code (unauthenticated).
     * Answers alike for unknown, verified and unverified addresses; see [requestPasswordReset].
     */
    fun resendEmailVerification(email: String): UUID {
        enforceCodeRequestCooldown(email, VerificationPurpose.VERIFY_EMAIL)
        val user = userRepository.findByEmail(email)
        if (user == null || user.emailVerified) return decoyVerificationId()

        val (verificationId, code) = verificationCodeService.createCode(email, VerificationPurpose.VERIFY_EMAIL)
        codeSendExecutor.execute { emailService.sendCode(email, code, "VERIFY_EMAIL") }
        return verificationId
    }

    /**
     * The once-a-minute cooldown, applied to every address. createCode enforces it only where a
     * code is actually issued, so a second request answered 409 for real accounts and 202 for
     * unknown ones.
     */
    private fun enforceCodeRequestCooldown(email: String, purpose: VerificationPurpose) {
        if (!rateLimiter.tryAcquire("code-request:$purpose:${email.lowercase()}", 1, CODE_REQUEST_COOLDOWN_SECONDS)) {
            throw IllegalStateException("Please wait before requesting a new code")
        }
    }

    /**
     * Stands in for a real verificationId. Hashes a throwaway code as createCode would, so the
     * response takes about as long; redeeming the id fails like a wrong code.
     */
    private fun decoyVerificationId(): UUID {
        passwordEncoder.encode(UUID.randomUUID().toString())
        return UUID.randomUUID()
    }

    /**
     * Change password (authenticated).
     * Verifies current password, updates to new password, revokes all refresh tokens.
     */
    @Transactional
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User not found")
        }

        if (AuthProvider.LOCAL !in user.providers || user.passwordHash == null) {
            throw IllegalStateException("No password credentials to change")
        }

        checkCurrentPassword(user, currentPassword)

        user.passwordHash = passwordEncoder.encode(newPassword)
        user.passwordTemporary = false
        user.requiredActions.remove(RequiredAction.UPDATE_PASSWORD)
        userRepository.save(user)
        refreshTokenRepository.deleteAllByUser(user)
    }

    /**
     * Request email change (authenticated).
     * Sends verification code to NEW email (proves ownership).
     * Uses userId as identifier for rate limiting (email changes during flow).
     */
    fun requestEmailChange(userId: UUID, newEmail: String, proof: ReauthProof): UUID {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User not found")
        }
        requireReauthentication(user, proof)

        if (userRepository.findByEmail(newEmail) != null) {
            throw IllegalStateException("Email already in use")
        }

        val (verificationId, code) = verificationCodeService.createCode(
            userId.toString(), VerificationPurpose.CHANGE_EMAIL, newValue = newEmail, userId = userId
        )
        emailService.sendCode(newEmail, code, "CHANGE_EMAIL")
        return verificationId
    }

    /**
     * Verify email change (authenticated).
     * Re-checks uniqueness at verify step (race condition protection).
     */
    @Transactional
    fun verifyEmailChange(userId: UUID, verificationId: UUID, code: String) {
        val verified = verificationCodeService.verifyCode(
            verificationId, userId.toString(), VerificationPurpose.CHANGE_EMAIL, code
        )

        val newEmail = verified.newValue
            ?: throw IllegalStateException("Missing new email value")

        // Race condition protection: re-check uniqueness at verify time
        if (userRepository.findByEmail(newEmail) != null) {
            throw IllegalStateException("Email already in use")
        }

        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User not found")
        }
        user.email = newEmail
        userRepository.save(user)
        // Whoever else holds a session must log in again — with the new address now in charge.
        refreshTokenRepository.deleteAllByUser(user)
    }

    /**
     * Request phone change (authenticated).
     * Normalizes to E.164, checks uniqueness, sends OTP via SMS.
     * Uses userId as identifier for rate limiting.
     */
    fun requestPhoneChange(userId: UUID, phone: String, proof: ReauthProof): UUID {
        val phoneE164 = normalizeToE164(phone)

        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User not found")
        }
        requireReauthentication(user, proof)

        if (userRepository.findByPhone(phoneE164) != null) {
            throw IllegalStateException("Phone number already in use")
        }

        val (verificationId, code) = verificationCodeService.createCode(
            userId.toString(), VerificationPurpose.CHANGE_PHONE, newValue = phoneE164, userId = userId
        )
        otpDeliveryService.sendCode(phoneE164, code)
        return verificationId
    }

    /**
     * Verify phone change (authenticated).
     * Re-checks uniqueness at verify step (race condition protection).
     * Ensures LOCAL provider is present.
     */
    @Transactional
    fun verifyPhoneChange(userId: UUID, verificationId: UUID, phone: String, code: String) {
        val phoneE164 = normalizeToE164(phone)

        val verified = verificationCodeService.verifyCode(
            verificationId, userId.toString(), VerificationPurpose.CHANGE_PHONE, code
        )
        val requestedPhone = verified.newValue
            ?: throw IllegalArgumentException("Phone verification request has no number")
        require(phoneE164 == requestedPhone) { "Phone does not match verification request" }

        // Race condition protection: re-check uniqueness at verify time
        if (userRepository.findByPhone(phoneE164) != null) {
            throw IllegalStateException("Phone number already in use")
        }

        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User not found")
        }
        user.phone = requestedPhone
        user.linkProvider(AuthProvider.LOCAL) // Idempotent — ensures LOCAL provider is present
        userRepository.save(user)
        refreshTokenRepository.deleteAllByUser(user)
    }

    /**
     * Sends a re-authentication code to the account's current email, or its phone if it has none.
     * Lets an account without a password prove ownership before changing its email or phone.
     */
    fun requestReauthCode(userId: UUID): ReauthCodeResponse {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User not found")
        }
        val phone = user.phone
        val channel = when {
            user.email.isNotBlank() -> "EMAIL"
            !phone.isNullOrBlank() -> "PHONE"
            else -> throw IllegalStateException("Account has no email or phone to send a code to")
        }
        val (verificationId, code) = verificationCodeService.createCode(
            userId.toString(), VerificationPurpose.REAUTH, userId = userId
        )
        if (channel == "EMAIL") emailService.sendCode(user.email, code, "REAUTH")
        else otpDeliveryService.sendCode(phone!!, code)
        return ReauthCodeResponse(verificationId, channel)
    }

    /**
     * A stolen access token must not be enough to move the account to an address the thief
     * controls. The caller proves ownership with the current password or a re-authentication code.
     * An account with neither a password nor an address to send a code to (Telegram-only) can
     * only add one right after logging in.
     */
    private fun requireReauthentication(user: User, proof: ReauthProof) {
        val password = proof.currentPassword
        val verificationId = proof.reauthVerificationId
        val code = proof.reauthCode
        when {
            password != null -> checkCurrentPassword(user, password)
            verificationId != null && code != null -> verificationCodeService.verifyCode(
                verificationId, user.id.toString(), VerificationPurpose.REAUTH, code
            )
            user.passwordHash == null && user.email.isBlank() && user.phone.isNullOrBlank() -> {
                val authTime = proof.authTime
                if (authTime == null || authTime.isBefore(Instant.now().minusSeconds(FRESH_LOGIN_SECONDS))) {
                    throw ForbiddenOperationException("Log in again to add an email or phone")
                }
            }
            else -> throw ForbiddenOperationException(
                "Reauthentication required: send currentPassword, or a code from /users/me/reauth/request"
            )
        }
    }

    // Shares the change-password counter: both guess the same secret.
    private fun checkCurrentPassword(user: User, currentPassword: String) {
        val hash = user.passwordHash
        if (AuthProvider.LOCAL !in user.providers || hash == null) {
            throw IllegalStateException("No password credentials to change")
        }
        val rule = rateLimitProperties.changePassword
        val key = "change-password:${user.id}"
        if (rateLimitProperties.enabled && !rateLimiter.tryAcquire(key, rule.maxAttempts, rule.windowSeconds)) {
            throw RateLimitExceededException("Too many attempts. Try again later.", rule.windowSeconds)
        }
        if (!passwordEncoder.matches(currentPassword, hash)) {
            throw BadCredentialsException("Current password is incorrect")
        }
        rateLimiter.reset(key)
    }

    companion object {
        private const val FRESH_LOGIN_SECONDS = 300L
        private const val CODE_REQUEST_COOLDOWN_SECONDS = 60L
    }
}

/** What the caller offered as proof of account ownership; see [AccountManagementService.requestEmailChange]. */
data class ReauthProof(
    val currentPassword: String? = null,
    val reauthVerificationId: UUID? = null,
    val reauthCode: String? = null,
    /** The access token's auth_time: when the caller last logged in. */
    val authTime: Instant? = null
)
