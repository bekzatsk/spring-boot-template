package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.OtpSendResult
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.shared.util.normalizeToE164
import kz.innlab.starter.user.service.UserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["app.auth.phone.enabled"], havingValue = "true", matchIfMissing = true)
class PhoneOtpService(
    private val verificationCodeService: VerificationCodeService,
    private val otpDeliveryService: OtpDeliveryService,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer,
    private val afterCommitRunner: AfterCommitRunner
) {

    /**
     * Send an OTP via SMS to the provided phone number.
     * Phone number is normalized to E.164 before sending.
     * Throws IllegalArgumentException (-> 400) on invalid phone format.
     * Throws IllegalStateException (-> 409) if rate limit exceeded (1 request per phone per 60s).
     * Returns an OtpSendResult with the verification UUID (to pass back to the verify endpoint)
     * and the resend cooldown info for the client.
     * Note: this path answers 409, not 429, unlike the login and change-password limits.
     * Changing it would break existing clients, so it is left to the API owner to decide.
     */
    @Transactional
    fun sendOtp(rawPhone: String): OtpSendResult {
        val phoneE164 = normalizeToE164(rawPhone)
        val issued = verificationCodeService.createCode(phoneE164, VerificationPurpose.PHONE_LOGIN)

        // Deferred to after commit: the SMS provider HTTP call must not hold the DB transaction open.
        afterCommitRunner.run { otpDeliveryService.sendCode(phoneE164, issued.code) }

        return OtpSendResult(
            verificationId = issued.verificationId,
            resendAvailableAt = issued.resendAvailableAt,
            retryAfterSeconds = issued.retryAfterSeconds
        )
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
        verificationCodeService.verifyCode(verificationId, phoneE164, VerificationPurpose.PHONE_LOGIN, code)
        val user = userService.findOrCreatePhoneUser(phoneE164)
        return authTokenIssuer.issue(user)
    }
}
