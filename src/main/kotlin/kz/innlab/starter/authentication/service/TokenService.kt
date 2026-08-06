package kz.innlab.starter.authentication.service

import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.config.AuthTokenProperties
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val authTokenProperties: AuthTokenProperties
) {

    fun generateAccessToken(
        userId: UUID,
        roles: Set<Role>,
        requiredActions: Set<RequiredAction> = emptySet()
    ): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("template-app")
            .issuedAt(now)
            .expiresAt(now.plus(authTokenProperties.accessToken.expiryMinutes, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("roles", roles.map { it.name })
            .claim("required_actions", requiredActions.map { it.name })
            .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
