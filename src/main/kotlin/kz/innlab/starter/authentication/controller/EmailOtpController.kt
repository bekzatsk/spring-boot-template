package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.EmailOtpRequest
import kz.innlab.starter.authentication.dto.EmailOtpVerifyRequest
import kz.innlab.starter.authentication.dto.OtpSendResult
import kz.innlab.starter.authentication.service.EmailOtpService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth/email")
@ConditionalOnProperty(name = ["app.auth.email-otp.enabled"], havingValue = "true", matchIfMissing = true)
class EmailOtpController(
    private val emailOtpService: EmailOtpService
) {

    @Operation(summary = "Request email OTP code for passwordless authentication", security = [])
    @PostMapping("/request")
    fun requestEmailOtp(@Valid @RequestBody request: EmailOtpRequest): ResponseEntity<OtpSendResult> =
        ResponseEntity.ok(emailOtpService.sendOtp(request.email))

    @Operation(summary = "Verify email OTP code and authenticate", security = [])
    @PostMapping("/verify")
    fun verifyEmailOtp(@Valid @RequestBody request: EmailOtpVerifyRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(emailOtpService.verifyOtp(request.verificationId, request.email, request.code))
}
