package kz.innlab.template.authentication.controller

import jakarta.validation.Valid
import kz.innlab.template.authentication.dto.AppleAuthRequest
import kz.innlab.template.authentication.dto.AuthRequest
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.authentication.dto.RefreshRequest
import kz.innlab.template.authentication.service.AppleOAuth2Service
import kz.innlab.template.authentication.service.GoogleOAuth2Service
import kz.innlab.template.authentication.service.RefreshTokenService
import kz.innlab.template.authentication.service.TokenService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val googleOAuth2Service: GoogleOAuth2Service,
    private val appleOAuth2Service: AppleOAuth2Service,
    private val refreshTokenService: RefreshTokenService,
    private val tokenService: TokenService
) {

    @PostMapping("/google")
    fun googleLogin(@Valid @RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        // TODO: rate limiting — add per-IP or per-client rate limit here before delegating to service
        val response = googleOAuth2Service.authenticate(request.idToken, request.name, request.picture)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/apple")
    fun appleLogin(@Valid @RequestBody request: AppleAuthRequest): ResponseEntity<AuthResponse> {
        // TODO: rate limiting — add per-IP or per-client rate limit here before delegating to service
        val response = appleOAuth2Service.authenticate(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        // TODO: rate limiting — refresh endpoint is a common abuse target; consider per-IP limit
        val (user, newRawToken) = refreshTokenService.rotate(request.refreshToken)
        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        return ResponseEntity.ok(AuthResponse(accessToken = accessToken, refreshToken = newRawToken))
    }

    @PostMapping("/revoke")
    fun revoke(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Void> {
        // TODO: rate limiting — optional; include in global auth rate limit policy
        refreshTokenService.revoke(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
