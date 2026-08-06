package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.OtpSendResult
import kz.innlab.starter.shared.util.normalizeToE164
import kz.innlab.starter.user.service.UserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["app.auth.phone.enabled"], havingValue = "true", matchIfMissing = true)
class PhoneOtpService(
    private val smsVerificationService: SmsVerificationService,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer
) {

    /**
     * Send an OTP via SMS to the provided phone number.
     * Phone number is normalized to E.164 before sending.
     * Throws IllegalArgumentException (-> 400) on invalid phone format.
     * Throws IllegalStateException (-> 409) if rate limit exceeded (1 request per phone per 60s).
     * Returns an OtpSendResult with the verification UUID (to pass back to the verify endpoint)
     * and the resend cooldown info for the client.
     * TODO: return a dedicated 429 Too Many Requests response for rate limit violations.
     */
    fun sendOtp(rawPhone: String): OtpSendResult {
        val phoneE164 = normalizeToE164(rawPhone)
        return smsVerificationService.sendCode(phoneE164)
    }

    /**
     * Verify an OTP code and issue JWT access + refresh tokens.
     * Throws IllegalArgumentException (-> 400) on invalid phone format.
     * Throws BadCredentialsException (-> 401) on invalid, expired, or too-many-attempts OTP.
     * verificationId must match the UUID returned by sendOtp — prevents brute-force on phone alone.
     */
    @Transactional
    fun verifyOtp(verificationId: UUID, rawPhone: String, code: String): AuthResponse {
        val phoneE164 = normalizeToE164(rawPhone)
        val verified = smsVerificationService.verifyCode(verificationId, phoneE164, code)
        if (!verified) {
            throw BadCredentialsException("Invalid or expired OTP")
        }
        val user = userService.findOrCreatePhoneUser(phoneE164)
        return authTokenIssuer.issue(user)
    }
}
