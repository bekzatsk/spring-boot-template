package kz.innlab.starter.user.service

import kz.innlab.starter.shared.error.ResourceNotFoundException
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.config.AuthTokenProperties
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authTokenProperties: AuthTokenProperties
) {

    /**
     * Admin-only user creation. Bypasses the registration.enabled gate.
     * Throws IllegalStateException (409) if email already exists.
     */
    @Transactional
    fun createUserByAdmin(
        email: String,
        rawPassword: String,
        name: String?,
        roles: Set<Role>?,
        temporary: Boolean = false
    ): User {
        if (userRepository.findByEmail(email) != null) {
            throw IllegalStateException("Email already registered")
        }
        val user = User(email = email).apply {
            this.name = name
            this.passwordHash = passwordEncoder.encode(rawPassword)
            this.passwordTemporary = temporary
            linkProvider(AuthProvider.LOCAL)
            if (!roles.isNullOrEmpty()) {
                this.roles.clear()
                this.roles.addAll(roles)
            }
            if (temporary) {
                this.requiredActions.add(RequiredAction.UPDATE_PASSWORD)
            }
        }
        return userRepository.save(user)
    }

    @Transactional
    fun findOrCreateGoogleUser(
        providerId: String,
        email: String,
        name: String?,
        picture: String?
    ): User {
        val existing = userRepository.findByEmail(email)
        if (existing != null) {
            // Link GOOGLE provider to existing account (idempotent)
            existing.linkProvider(AuthProvider.GOOGLE, providerId)
            // Only update name/picture if currently null on existing user
            if (existing.name == null && name != null) existing.name = name
            if (existing.picture == null && picture != null) existing.picture = picture
            return userRepository.save(existing)
        }
        if (!authTokenProperties.registration.enabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = email).also {
                it.linkProvider(AuthProvider.GOOGLE, providerId)
                it.name = name
                it.picture = picture
            }
        )
    }

    @Transactional
    fun findOrCreateAppleUser(
        providerId: String,
        email: String?,   // Nullable: absent on subsequent Apple logins — this is expected
        name: String?
    ): User {
        // Step 1: Try Apple sub lookup — covers returning users where email is absent
        val byAppleSub = userRepository.findByAppleProviderId(providerId)
        if (byAppleSub != null) {
            return byAppleSub  // Returning user — no changes needed
        }

        // Step 2: First sign-in — email MUST be present
        val resolvedEmail = email
            ?: throw BadCredentialsException(
                "Email not present in Apple identity token on first sign-in"
            )

        // Step 3: Check if email already exists — link APPLE to existing account
        val byEmail = userRepository.findByEmail(resolvedEmail)
        if (byEmail != null) {
            byEmail.linkProvider(AuthProvider.APPLE, providerId)
            if (byEmail.name == null && name != null) byEmail.name = name
            return userRepository.save(byEmail)
        }

        // Step 4: Create new user
        if (!authTokenProperties.registration.enabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = resolvedEmail).also {
                it.linkProvider(AuthProvider.APPLE, providerId)
                it.name = name
            }
        )
    }

    /**
     * Find an existing phone user or create a new one (find-or-create pattern).
     * Phone users are looked up by phone field (unique column).
     * Email is set to empty string since the column is NOT NULL.
     */
    @Transactional
    fun findOrCreatePhoneUser(phoneE164: String): User {
        val existing = userRepository.findByPhone(phoneE164)
        if (existing != null) return existing

        if (!authTokenProperties.registration.enabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = "").also {
                it.linkProvider(AuthProvider.LOCAL)
                it.phone = phoneE164
            }
        )
    }

    /** Find or create a passwordless email user after ownership was proven by OTP. */
    @Transactional
    fun findOrCreateEmailOtpUser(email: String): User {
        val existing = userRepository.findByEmailIgnoreCase(email)
        if (existing != null) {
            existing.linkProvider(AuthProvider.LOCAL)
            existing.emailVerified = true
            existing.requiredActions.remove(RequiredAction.VERIFY_EMAIL)
            return userRepository.save(existing)
        }

        if (!authTokenProperties.registration.enabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = email).also {
                it.linkProvider(AuthProvider.LOCAL)
                it.emailVerified = true
            }
        )
    }

    @Transactional
    fun findOrCreateTelegramUser(telegramUserId: Long, telegramUsername: String?): User {
        val existing = userRepository.findByTelegramUserId(telegramUserId)
        if (existing != null) {
            if (telegramUsername != null && existing.telegramUsername != telegramUsername) {
                existing.telegramUsername = telegramUsername
                return userRepository.save(existing)
            }
            return existing
        }

        if (!authTokenProperties.registration.enabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = "").also {
                it.linkProvider(AuthProvider.TELEGRAM, telegramUserId.toString())
                it.telegramUserId = telegramUserId
                it.telegramUsername = telegramUsername
            }
        )
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): User =
        userRepository.findById(id).orElseThrow { ResourceNotFoundException("User not found") }
}
