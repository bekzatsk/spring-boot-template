package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.OtpSendResult
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.user.service.UserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["app.auth.email-otp.enabled"], havingValue = "true", matchIfMissing = true)
class EmailOtpService(
    private val verificationCodeService: VerificationCodeService,
    private val emailService: EmailService,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer
) {

    @Transactional
    fun sendOtp(rawEmail: String): OtpSendResult {
        val email = normalizeEmail(rawEmail)
        val issued = verificationCodeService.createCode(email, VerificationPurpose.EMAIL_LOGIN)
        emailService.sendCode(email, issued.code, "EMAIL_LOGIN")
        return OtpSendResult(
            verificationId = issued.verificationId,
            resendAvailableAt = issued.resendAvailableAt,
            retryAfterSeconds = issued.retryAfterSeconds
        )
    }

    @Transactional
    fun verifyOtp(verificationId: UUID, rawEmail: String, code: String): AuthResponse {
        val email = normalizeEmail(rawEmail)
        verificationCodeService.verifyCode(verificationId, email, VerificationPurpose.EMAIL_LOGIN, code)
        val user = userService.findOrCreateEmailOtpUser(email)
        return authTokenIssuer.issue(user)
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
}
