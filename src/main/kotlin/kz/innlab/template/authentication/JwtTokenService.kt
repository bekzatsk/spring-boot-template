package kz.innlab.template.authentication

import kz.innlab.template.user.model.Role
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class JwtTokenService(
    private val jwtEncoder: JwtEncoder
) {

    fun generateAccessToken(userId: UUID, roles: Set<Role>): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("template-app")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("roles", roles.map { it.name })
            .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
