package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.PhoneOtpRequest
import kz.innlab.starter.authentication.dto.PhoneVerifyRequest
import kz.innlab.starter.authentication.service.PhoneOtpService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["app.auth.phone.enabled"], havingValue = "true", matchIfMissing = true)
class PhoneAuthController(
    private val phoneOtpService: PhoneOtpService
) {

    @Operation(summary = "Request SMS OTP code for phone authentication", security = [])
    @PostMapping("/phone/request")
    fun requestPhoneOtp(@Valid @RequestBody request: PhoneOtpRequest): ResponseEntity<Map<String, Any>> {
        val verificationId = phoneOtpService.sendOtp(request.phone)
        return ResponseEntity.ok(mapOf("verificationId" to verificationId))
    }

    @Operation(summary = "Verify SMS OTP code and authenticate", security = [])
    @PostMapping("/phone/verify")
    fun verifyPhoneOtp(@Valid @RequestBody request: PhoneVerifyRequest): ResponseEntity<AuthResponse> {
        val response = phoneOtpService.verifyOtp(request.verificationId, request.phone, request.code)
        return ResponseEntity.ok(response)
    }
}
