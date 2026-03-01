package kz.innlab.template.authentication.service

import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.User
import kz.innlab.template.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LocalAuthService(
    @Qualifier("localAuthenticationManager")
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {

    /**
     * Register a new LOCAL email+password user.
     * Throws IllegalStateException (409 Conflict) if email already registered under LOCAL provider.
     */
    @Transactional
    fun register(email: String, rawPassword: String, name: String?): AuthResponse {
        if (userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email) != null) {
            throw IllegalStateException("Email already registered")
        }

        val user = User(
            email = email,
            provider = AuthProvider.LOCAL,
            providerId = email  // For LOCAL email users, providerId = email
        )
        user.name = name
        user.passwordHash = passwordEncoder.encode(rawPassword)

        val savedUser = userRepository.save(user)

        val accessToken = tokenService.generateAccessToken(savedUser.id!!, savedUser.roles)
        val refreshToken = refreshTokenService.createToken(savedUser)

        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }

    /**
     * Authenticate a LOCAL email+password user.
     * Throws BadCredentialsException (401) on invalid credentials via DaoAuthenticationProvider.
     */
    fun login(email: String, rawPassword: String): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)
        )

        val user = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email)
            ?: throw BadCredentialsException("User not found")

        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)

        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
