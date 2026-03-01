package kz.innlab.template.authentication

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.user.service.UserService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleAuthService(
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val userService: UserService,
    private val jwtTokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
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

        val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)

        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
