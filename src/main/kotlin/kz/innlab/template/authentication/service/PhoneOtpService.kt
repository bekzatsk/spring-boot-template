package kz.innlab.template.authentication.service

import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.user.service.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PhoneOtpService(
    private val twilioVerifyClient: TwilioVerifyClient,
    @Value("\${app.auth.twilio.verify-service-sid}")
    private val serviceSid: String,
    private val userService: UserService,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {

    /**
     * Send an OTP via SMS to the provided phone number.
     * Phone number is normalized to E.164 before sending to Twilio.
     * Throws IllegalArgumentException (-> 400) on invalid phone format.
     */
    fun sendOtp(rawPhone: String) {
        val phoneE164 = normalizeToE164(rawPhone)
        twilioVerifyClient.sendVerification(serviceSid, phoneE164, "sms")
        // No return value (204 response) — do not leak whether phone exists in DB
    }

    /**
     * Verify an OTP code and issue JWT access + refresh tokens.
     * Throws IllegalArgumentException (-> 400) on invalid phone format.
     * Throws BadCredentialsException (-> 401) on invalid or expired OTP.
     */
    @Transactional
    fun verifyOtp(rawPhone: String, code: String): AuthResponse {
        val phoneE164 = normalizeToE164(rawPhone)
        val status = twilioVerifyClient.checkVerification(serviceSid, phoneE164, code)
        if (status != "approved") {
            throw BadCredentialsException("Invalid or expired OTP")
        }
        val user = userService.findOrCreatePhoneUser(phoneE164)
        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)
        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
