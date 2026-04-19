package kz.innlab.starter.user.service

import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    @Value("\${app.auth.registration.enabled:true}") private val registrationEnabled: Boolean = true
) {

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
            existing.providers.add(AuthProvider.GOOGLE)
            existing.providerIds[AuthProvider.GOOGLE] = providerId
            // Only update name/picture if currently null on existing user
            if (existing.name == null && name != null) existing.name = name
            if (existing.picture == null && picture != null) existing.picture = picture
            return userRepository.save(existing)
        }
        if (!registrationEnabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = email).also {
                it.providers.add(AuthProvider.GOOGLE)
                it.providerIds[AuthProvider.GOOGLE] = providerId
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
            byEmail.providers.add(AuthProvider.APPLE)
            byEmail.providerIds[AuthProvider.APPLE] = providerId
            if (byEmail.name == null && name != null) byEmail.name = name
            return userRepository.save(byEmail)
        }

        // Step 4: Create new user
        if (!registrationEnabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = resolvedEmail).also {
                it.providers.add(AuthProvider.APPLE)
                it.providerIds[AuthProvider.APPLE] = providerId
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

        if (!registrationEnabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        return userRepository.save(
            User(email = "").also {
                it.providers.add(AuthProvider.LOCAL)
                it.phone = phoneE164
            }
        )
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): User =
        userRepository.findById(id).orElseThrow { AccessDeniedException("User not found") }
}
