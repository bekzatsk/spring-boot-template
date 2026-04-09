package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(name = ["app.auth.local.enabled"], havingValue = "true", matchIfMissing = true)
class LocalAuthService(
    @Qualifier("localAuthenticationManager")
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {

    /**
     * Register a new LOCAL email+password user, or link LOCAL credentials to existing social account.
     * - If email exists AND has password: 409 Conflict (already registered)
     * - If email exists AND no password: link LOCAL provider, set password (social user adding local credentials)
     * - If email not found: create new LOCAL user
     */
    @Transactional
    fun register(email: String, rawPassword: String, name: String?): AuthResponse {
        val existing = userRepository.findByEmail(email)

        if (existing != null && existing.passwordHash != null) {
            throw IllegalStateException("Email already registered")
        }

        val user = if (existing != null) {
            // Existing social account — link LOCAL provider and set password
            existing.providers.add(AuthProvider.LOCAL)
            existing.passwordHash = passwordEncoder.encode(rawPassword)
            if (existing.name == null && name != null) existing.name = name
            userRepository.save(existing)
        } else {
            // New user
            val newUser = User(email = email)
            newUser.providers.add(AuthProvider.LOCAL)
            newUser.name = name
            newUser.passwordHash = passwordEncoder.encode(rawPassword)
            userRepository.save(newUser)
        }

        val accessToken = tokenService.generateAccessToken(user.id, user.roles)
        val refreshToken = refreshTokenService.createToken(user)

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

        val user = userRepository.findByEmail(email)
            ?: throw BadCredentialsException("User not found")

        val accessToken = tokenService.generateAccessToken(user.id, user.roles)
        val refreshToken = refreshTokenService.createToken(user)

        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
