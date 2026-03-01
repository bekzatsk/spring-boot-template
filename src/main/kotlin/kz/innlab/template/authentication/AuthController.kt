package kz.innlab.template.authentication

import jakarta.validation.Valid
import kz.innlab.template.authentication.dto.AppleAuthRequest
import kz.innlab.template.authentication.dto.AuthRequest
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.authentication.dto.RefreshRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val googleAuthService: GoogleAuthService,
    private val appleAuthService: AppleAuthService,
    private val refreshTokenService: RefreshTokenService,
    private val jwtTokenService: JwtTokenService
) {

    @PostMapping("/google")
    fun googleLogin(@Valid @RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        val response = googleAuthService.authenticate(request.idToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/apple")
    fun appleLogin(@Valid @RequestBody request: AppleAuthRequest): ResponseEntity<AuthResponse> {
        val response = appleAuthService.authenticate(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val (user, newRawToken) = refreshTokenService.rotate(request.refreshToken)
        val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
        return ResponseEntity.ok(AuthResponse(accessToken = accessToken, refreshToken = newRawToken))
    }

    @PostMapping("/revoke")
    fun revoke(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Void> {
        refreshTokenService.revoke(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
