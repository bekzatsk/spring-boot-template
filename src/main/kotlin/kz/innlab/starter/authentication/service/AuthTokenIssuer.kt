package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.user.model.User
import org.springframework.stereotype.Service

/**
 * Single place that turns an authenticated [User] into an [AuthResponse]
 * (access token + refresh token + required actions). Every auth provider
 * (local, Google, Apple, phone OTP, Telegram) delegates here so a change to
 * token contents happens once, not once per provider.
 */
@Service
class AuthTokenIssuer(
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {

    fun issue(user: User): AuthResponse = AuthResponse(
        accessToken = tokenService.generateAccessToken(user.id, user.roles, user.requiredActions),
        refreshToken = refreshTokenService.createToken(user),
        requiredActions = user.requiredActions.map { it.name }
    )
}
