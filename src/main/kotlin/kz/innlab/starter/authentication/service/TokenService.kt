package kz.innlab.starter.authentication.service

import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.config.JwtIdentityProperties
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
    private val authTokenProperties: AuthTokenProperties,
    private val jwtIdentityProperties: JwtIdentityProperties
) {

    fun generateAccessToken(
        userId: UUID,
        roles: Set<Role>,
        requiredActions: Set<RequiredAction> = emptySet(),
        authTime: Instant = Instant.now(),
        groups: Set<String> = emptySet()
    ): String {
        val now = Instant.now()
        val builder = JwtClaimsSet.builder()
            .issuer(jwtIdentityProperties.issuer)
            .audience(listOf(jwtIdentityProperties.audience))
            .issuedAt(now)
            .expiresAt(now.plus(authTokenProperties.accessToken.expiryMinutes, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("roles", roles.map { it.name })
            .claim("required_actions", requiredActions.map { it.name })
            // When the user logged in, not when this token was minted: refresh keeps it.
            .claim("auth_time", authTime.epochSecond)
        // Directory groups (Active Directory, Entra ID) for the application's own authorization;
        // left out for everyone else so ordinary tokens stay as they were.
        if (groups.isNotEmpty()) builder.claim("groups", groups.sorted())
        return jwtEncoder.encode(JwtEncoderParameters.from(builder.build())).tokenValue
    }
}
