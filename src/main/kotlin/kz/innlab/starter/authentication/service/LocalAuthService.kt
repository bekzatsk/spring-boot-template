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
import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.config.RateLimitProperties
import kz.innlab.starter.shared.ratelimit.RateLimitExceededException
import kz.innlab.starter.shared.ratelimit.RateLimiter
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
    private val authTokenIssuer: AuthTokenIssuer,
    private val verificationCodeService: VerificationCodeService,
    private val emailService: EmailService,
    private val authTokenProperties: AuthTokenProperties,
    private val rateLimiter: RateLimiter,
    private val rateLimitProperties: RateLimitProperties
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
            existing.linkProvider(AuthProvider.LOCAL)
            existing.passwordHash = passwordEncoder.encode(rawPassword)
            if (existing.name == null && name != null) existing.name = name
            userRepository.save(existing)
        } else {
            if (!authTokenProperties.registration.enabled) {
                throw IllegalStateException("Registration is currently disabled")
            }
            // New user
            val newUser = User(email = email)
            newUser.linkProvider(AuthProvider.LOCAL)
            newUser.name = name
            newUser.passwordHash = passwordEncoder.encode(rawPassword)
            if (authTokenProperties.emailVerification.enabled) {
                // Soft gate: user is issued tokens but must verify email before accessing protected APIs.
                // Enforcement is via the VERIFY_EMAIL required action (RequiredActionFilter).
                newUser.emailVerified = false
                newUser.requiredActions.add(RequiredAction.VERIFY_EMAIL)
            }
            userRepository.save(newUser)
        }

        // Send the verification code only for genuinely new LOCAL registrations.
        var verificationId: java.util.UUID? = null
        if (isNewUser && authTokenProperties.emailVerification.enabled) {
            val (id, code) = verificationCodeService.createCode(email, VerificationPurpose.VERIFY_EMAIL)
            verificationId = id
            emailService.sendCode(email, code, "VERIFY_EMAIL")
        }

        return authTokenIssuer.issue(user).copy(verificationId = verificationId)
    }

    /**
     * Authenticate a LOCAL email+password user.
     * Throws BadCredentialsException (401) on invalid credentials via DaoAuthenticationProvider.
     */
    fun login(email: String, rawPassword: String): AuthResponse {
        // Without this the password check is an unlimited oracle: an attacker can try
        // candidates as fast as the server answers.
        val rule = rateLimitProperties.login
        val key = "login:${email.lowercase()}"
        if (rateLimitProperties.enabled && !rateLimiter.tryAcquire(key, rule.maxAttempts, rule.windowSeconds)) {
            throw RateLimitExceededException(
                "Too many login attempts. Try again later.",
                rule.windowSeconds
            )
        }

        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)
        )

        // Successful login clears the counter so a legitimate user is not locked out by
        // someone else guessing against their address.
        rateLimiter.reset(key)

        val user = userRepository.findByEmail(email)
            ?: throw BadCredentialsException("User not found")

        return authTokenIssuer.issue(user)
    }
}
