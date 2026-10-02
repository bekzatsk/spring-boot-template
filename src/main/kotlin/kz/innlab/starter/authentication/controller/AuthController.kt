package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kz.innlab.starter.authentication.cookie.AuthCookieWriter
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.RefreshRequest
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.TokenService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
class AuthController(
    private val refreshTokenService: RefreshTokenService,
    private val tokenService: TokenService,
    private val cookieWriter: ObjectProvider<AuthCookieWriter>
) {

    /** Browser clients obtain this token before sending a cookie-authenticated write request. */
    @GetMapping("/csrf")
    fun csrf(@RequestAttribute(name = "_csrf", required = false) token: CsrfToken?): ResponseEntity<Map<String, String>> =
        if (token == null) ResponseEntity.notFound().build()
        else ResponseEntity.ok(mapOf("token" to token.token, "headerName" to token.headerName))

    @Operation(summary = "Refresh access token using refresh token", security = [])
    @PostMapping("/refresh")
    fun refresh(
        @RequestBody(required = false) request: RefreshRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val rawToken = resolveRefreshToken(request, httpRequest)
            ?: throw BadCredentialsException("Refresh token is required")
        val (user, newRawToken) = refreshTokenService.rotate(rawToken)
        val accessToken = tokenService.generateAccessToken(user.id, user.roles, user.requiredActions)
        // Cookies (access + rotated refresh) are set centrally by AuthResponseCookieAdvice.
        return ResponseEntity.ok(AuthResponse(
            accessToken = accessToken,
            refreshToken = newRawToken,
            requiredActions = user.requiredActions.map { it.name }
        ))
    }

    @Operation(summary = "Revoke a refresh token", security = [])
    @PostMapping("/revoke")
    fun revoke(
        @RequestBody(required = false) request: RefreshRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        resolveRefreshToken(request, httpRequest)?.let { refreshTokenService.revoke(it) }
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Logout — revoke refresh token and clear auth cookies", security = [])
    @PostMapping("/logout")
    fun logout(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<Void> {
        val writer = cookieWriter.ifAvailable
        writer?.readRefreshToken(httpRequest)?.let { refreshTokenService.revoke(it) }
        writer?.let {
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, it.clearAccessCookie().toString())
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, it.clearRefreshCookie().toString())
        }
        return ResponseEntity.noContent().build()
    }

    /** Resolve the refresh token: request body first, then the refresh cookie (cookie mode). */
    private fun resolveRefreshToken(request: RefreshRequest?, httpRequest: HttpServletRequest): String? {
        val fromBody = request?.refreshToken?.takeIf { it.isNotBlank() }
        if (fromBody != null) return fromBody
        return cookieWriter.ifAvailable?.readRefreshToken(httpRequest)
    }
}
