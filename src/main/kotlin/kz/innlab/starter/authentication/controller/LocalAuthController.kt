package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.ForgotPasswordRequest
import kz.innlab.starter.authentication.dto.LocalLoginRequest
import kz.innlab.starter.authentication.dto.LocalRegisterRequest
import kz.innlab.starter.authentication.dto.ResetPasswordRequest
import kz.innlab.starter.authentication.service.AccountManagementService
import kz.innlab.starter.authentication.service.LocalAuthService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["app.auth.local.enabled"], havingValue = "true", matchIfMissing = true)
class LocalAuthController(
    private val localAuthService: LocalAuthService,
    private val accountManagementService: AccountManagementService
) {

    @Operation(summary = "Register with email and password", security = [])
    @PostMapping("/local/register")
    fun localRegister(@Valid @RequestBody request: LocalRegisterRequest): ResponseEntity<AuthResponse> {
        val response = localAuthService.register(request.email, request.password, request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "Login with email and password", security = [])
    @PostMapping("/local/login")
    fun localLogin(@Valid @RequestBody request: LocalLoginRequest): ResponseEntity<AuthResponse> {
        val response = localAuthService.login(request.email, request.password)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Request password reset verification code", security = [])
    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<Map<String, Any?>> {
        val verificationId = accountManagementService.requestPasswordReset(request.email)
        return ResponseEntity.accepted().body(mapOf("verificationId" to verificationId))
    }

    @Operation(summary = "Reset password with verification code", security = [])
    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
        accountManagementService.resetPassword(request.verificationId, request.email, request.code, request.newPassword)
        return ResponseEntity.ok().build()
    }
}
