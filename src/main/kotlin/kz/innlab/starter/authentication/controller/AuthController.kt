package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.RefreshRequest
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.TokenService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
class AuthController(
    private val refreshTokenService: RefreshTokenService,
    private val tokenService: TokenService
) {

    @Operation(summary = "Refresh access token using refresh token", security = [])
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val (user, newRawToken) = refreshTokenService.rotate(request.refreshToken)
        val accessToken = tokenService.generateAccessToken(user.id, user.roles)
        return ResponseEntity.ok(AuthResponse(accessToken = accessToken, refreshToken = newRawToken))
    }

    @Operation(summary = "Revoke a refresh token", security = [])
    @PostMapping("/revoke")
    fun revoke(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Void> {
        refreshTokenService.revoke(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
