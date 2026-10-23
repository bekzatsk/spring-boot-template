package kz.innlab.starter.authentication.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.config.GoogleAuthProperties
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.service.UserService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["app.auth.google.enabled"], havingValue = "true")
class GoogleOAuth2Service(
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer,
    private val googleAuthProperties: GoogleAuthProperties = GoogleAuthProperties()
) {

    // Deliberately NOT @Transactional: token verification is a remote HTTP call (Google certs)
    // and must not hold a DB connection. DB work happens in the transactional
    // userService.findOrCreateGoogleUser / refreshTokenService.createToken calls.
    fun authenticate(idTokenString: String, clientName: String? = null, clientPicture: String? = null): AuthResponse {
        val payload = verify(idTokenString)
        val name = payload["name"] as? String ?: clientName
        val picture = payload["picture"] as? String ?: clientPicture

        val user = userService.findOrCreateGoogleUser(
            payload.subject, payload.email, name, picture, googleAuthProperties.linkByVerifiedEmail
        )

        return authTokenIssuer.issue(user)
    }

    /** Links the Google account behind [idTokenString] to the signed-in user [userId]. */
    fun link(userId: UUID, idTokenString: String): User {
        val payload = verify(idTokenString)
        return userService.linkGoogleAccount(userId, payload.subject, payload.email)
    }

    /** Returns the payload of a valid token whose email Google has verified. */
    private fun verify(idTokenString: String): GoogleIdToken.Payload {
        val idToken = googleIdTokenVerifier.verify(idTokenString)
            ?: throw BadCredentialsException("Invalid Google ID token")
        val payload = idToken.payload
        if (payload.email == null) {
            throw BadCredentialsException("Google account does not have an email")
        }
        if (payload.emailVerified != true) {
            throw BadCredentialsException("Google account email is not verified")
        }
        return payload
    }
}
