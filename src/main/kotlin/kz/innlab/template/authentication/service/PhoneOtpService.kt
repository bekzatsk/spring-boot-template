package kz.innlab.template.authentication.service

import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.user.service.UserService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PhoneOtpService(
    private val smsVerificationService: SmsVerificationService,
    private val userService: UserService,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {

    /**
     * Send an OTP via SMS to the provided phone number.
     * Phone number is normalized to E.164 before sending.
     * Throws IllegalArgumentException (-> 400) on invalid phone format.
     * Throws IllegalStateException (-> 409) if rate limit exceeded (1 request per phone per 60s).
     * Returns the UUID of the created SmsVerification record, to be passed back to the verify endpoint.
     * TODO: return a dedicated 429 Too Many Requests response for rate limit violations.
     */
    fun sendOtp(rawPhone: String): UUID {
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
        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)
        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
