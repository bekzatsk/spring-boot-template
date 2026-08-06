package kz.innlab.starter.authentication.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.user.service.UserService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["app.auth.google.enabled"], havingValue = "true")
class GoogleOAuth2Service(
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer
) {

    // Deliberately NOT @Transactional: token verification is a remote HTTP call (Google certs)
    // and must not hold a DB connection. DB work happens in the transactional
    // userService.findOrCreateGoogleUser / refreshTokenService.createToken calls.
    fun authenticate(idTokenString: String, clientName: String? = null, clientPicture: String? = null): AuthResponse {
        val idToken = googleIdTokenVerifier.verify(idTokenString)
            ?: throw BadCredentialsException("Invalid Google ID token")

        val payload = idToken.payload

        val providerId = payload.subject
        val email = payload.email
            ?: throw BadCredentialsException("Google account does not have an email")
        val name = payload["name"] as? String ?: clientName
        val picture = payload["picture"] as? String ?: clientPicture

        val user = userService.findOrCreateGoogleUser(providerId, email, name, picture)

        return authTokenIssuer.issue(user)
    }
}
