package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.repository.UserRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccountManagementService(
    private val userRepository: UserRepository,
    private val verificationCodeService: VerificationCodeService,
    private val emailService: EmailService,
    private val smsService: SmsService,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    /**
     * Request password reset (unauthenticated).
     * Returns verificationId if email exists with LOCAL+password, null otherwise (anti-enumeration).
     */
    fun requestPasswordReset(email: String): UUID? {
        val user = userRepository.findByEmail(email) ?: return null

        // Social-only users have no password to reset
        if (AuthProvider.LOCAL !in user.providers || user.passwordHash == null) {
            return null
        }

        val (verificationId, code) = verificationCodeService.createCode(email, VerificationPurpose.FORGOT_PASSWORD)
        emailService.sendCode(email, code, "FORGOT_PASSWORD")
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
        userRepository.save(user)
        refreshTokenRepository.deleteAllByUser(user)
    }

    /**
     * Change password (authenticated).
     * Verifies current password, updates to new password, revokes all refresh tokens.
     */
    @Transactional
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId).orElseThrow {
            AccessDeniedException("User not found")
        }

        if (AuthProvider.LOCAL !in user.providers || user.passwordHash == null) {
            throw IllegalStateException("No password credentials to change")
        }

        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw BadCredentialsException("Current password is incorrect")
        }

        user.passwordHash = passwordEncoder.encode(newPassword)
        userRepository.save(user)
        refreshTokenRepository.deleteAllByUser(user)
    }

    /**
     * Request email change (authenticated).
     * Sends verification code to NEW email (proves ownership).
     * Uses userId as identifier for rate limiting (email changes during flow).
     */
    fun requestEmailChange(userId: UUID, newEmail: String): UUID {
        val user = userRepository.findById(userId).orElseThrow {
            AccessDeniedException("User not found")
        }

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
            AccessDeniedException("User not found")
        }
        user.email = newEmail
        userRepository.save(user)
    }

    /**
     * Request phone change (authenticated).
     * Normalizes to E.164, checks uniqueness, sends OTP via SMS.
     * Uses userId as identifier for rate limiting.
     */
    fun requestPhoneChange(userId: UUID, phone: String): UUID {
        val phoneE164 = normalizeToE164(phone)

        val user = userRepository.findById(userId).orElseThrow {
            AccessDeniedException("User not found")
        }

        if (userRepository.findByPhone(phoneE164) != null) {
            throw IllegalStateException("Phone number already in use")
        }

        val (verificationId, code) = verificationCodeService.createCode(
            userId.toString(), VerificationPurpose.CHANGE_PHONE, newValue = phoneE164, userId = userId
        )
        smsService.sendCode(phoneE164, code)
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

        verificationCodeService.verifyCode(
            verificationId, userId.toString(), VerificationPurpose.CHANGE_PHONE, code
        )

        // Race condition protection: re-check uniqueness at verify time
        if (userRepository.findByPhone(phoneE164) != null) {
            throw IllegalStateException("Phone number already in use")
        }

        val user = userRepository.findById(userId).orElseThrow {
            AccessDeniedException("User not found")
        }
        user.phone = phoneE164
        user.providers.add(AuthProvider.LOCAL) // Idempotent — ensures LOCAL provider is present
        userRepository.save(user)
    }
}
