package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.beans.factory.annotation.Value
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
    private val refreshTokenService: RefreshTokenService,
    private val verificationCodeService: VerificationCodeService,
    private val emailService: EmailService,
    @Value("\${app.auth.registration.enabled:true}") private val registrationEnabled: Boolean = true,
    @Value("\${app.auth.email-verification.enabled:false}") private val emailVerificationEnabled: Boolean = false
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

        val isNewUser = existing == null

        val user = if (existing != null) {
            // Existing social account — link LOCAL provider and set password.
            // Email is already owned/verified via the social provider — no re-verification.
            existing.providers.add(AuthProvider.LOCAL)
            existing.passwordHash = passwordEncoder.encode(rawPassword)
            if (existing.name == null && name != null) existing.name = name
            userRepository.save(existing)
        } else {
            if (!registrationEnabled) {
                throw IllegalStateException("Registration is currently disabled")
            }
            // New user
            val newUser = User(email = email)
            newUser.providers.add(AuthProvider.LOCAL)
            newUser.name = name
            newUser.passwordHash = passwordEncoder.encode(rawPassword)
            if (emailVerificationEnabled) {
                // Soft gate: user is issued tokens but must verify email before accessing protected APIs.
                // Enforcement is via the VERIFY_EMAIL required action (RequiredActionFilter).
                newUser.emailVerified = false
                newUser.requiredActions.add(RequiredAction.VERIFY_EMAIL)
            }
            userRepository.save(newUser)
        }

        // Send the verification code only for genuinely new LOCAL registrations.
        if (isNewUser && emailVerificationEnabled) {
            val (_, code) = verificationCodeService.createCode(email, VerificationPurpose.VERIFY_EMAIL)
            emailService.sendCode(email, code, "VERIFY_EMAIL")
        }

        val accessToken = tokenService.generateAccessToken(user.id, user.roles, user.requiredActions)
        val refreshToken = refreshTokenService.createToken(user)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            requiredActions = user.requiredActions.map { it.name }
        )
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

        val accessToken = tokenService.generateAccessToken(user.id, user.roles, user.requiredActions)
        val refreshToken = refreshTokenService.createToken(user)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            requiredActions = user.requiredActions.map { it.name }
        )
    }
}
