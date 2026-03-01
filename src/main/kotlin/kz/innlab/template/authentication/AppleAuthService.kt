package kz.innlab.template.authentication

import kz.innlab.template.authentication.dto.AppleAuthRequest
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.user.UserService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppleAuthService(
    @Qualifier("appleJwtDecoder")
    private val appleJwtDecoder: JwtDecoder,
    private val userService: UserService,
    private val jwtTokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
    fun authenticate(request: AppleAuthRequest): AuthResponse {
        // Wrap JwtException (not a BadCredentialsException subtype) so GlobalExceptionHandler returns 401
        val jwt = try {
            appleJwtDecoder.decode(request.idToken)
        } catch (ex: Exception) {
            throw BadCredentialsException("Invalid Apple ID token: ${ex.message}", ex)
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

        val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)

        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
