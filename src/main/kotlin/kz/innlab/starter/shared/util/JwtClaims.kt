package kz.innlab.starter.shared.util

import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * When the user last logged in, from the access token's `auth_time` claim. Refresh keeps it, so
 * it is the login time, not the token's mint time. Null for tokens minted before the claim
 * existed: callers treat that as "not recent".
 */
fun Jwt.authTime(): Instant? = when (val claim = claims["auth_time"]) {
    is Instant -> claim
    is Number -> Instant.ofEpochSecond(claim.toLong())
    else -> null
}
