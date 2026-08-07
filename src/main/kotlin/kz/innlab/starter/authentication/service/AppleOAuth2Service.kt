package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.AppleAuthRequest
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["app.auth.apple.enabled"], havingValue = "true")
class AppleOAuth2Service(
    @Qualifier("appleJwtDecoder")
    private val appleJwtDecoder: JwtDecoder,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AppleOAuth2Service::class.java)
    }

    // Deliberately NOT @Transactional: JWT decoding fetches Apple's JWKS over HTTP and must not
    // hold a DB connection. DB work happens in the transactional userService/refreshTokenService calls.
    fun authenticate(request: AppleAuthRequest): AuthResponse {
        // Wrap JwtException (not a BadCredentialsException subtype) so AuthExceptionHandler returns 401
        val jwt = try {
            appleJwtDecoder.decode(request.idToken)
        } catch (ex: Exception) {
            // Detail stays server-side: the message is returned verbatim in the 401 body and the
            // underlying Nimbus/JWKS failure can expose internal network and parser state.
            logger.warn("Apple ID token validation failed: {}", ex.message, ex)
            throw BadCredentialsException("Invalid Apple ID token", ex)
        }

        val sub: String = jwt.subject
            ?: throw BadCredentialsException("Apple identity token missing sub claim")

        // Email is present on first login, absent on subsequent logins — this is expected Apple behavior
        val email: String? = jwt.getClaimAsString("email")

        // Name is NEVER in the Apple JWT — iOS client sends it separately in the request body
        val givenName: String? = request.givenName?.takeIf { it.isNotBlank() }
        val familyName: String? = request.familyName?.takeIf { it.isNotBlank() }

        val fullName: String? = listOfNotNull(givenName, familyName)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        val user = userService.findOrCreateAppleUser(
            providerId = sub,
            email = email,
            name = fullName
        )

        return authTokenIssuer.issue(user)
    }
}
