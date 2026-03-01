package kz.innlab.template.user

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository
) {

    @Transactional
    fun findOrCreateGoogleUser(
        providerId: String,
        email: String,
        name: String?,
        picture: String?
    ): User {
        val existing = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
        if (existing != null) {
            var updated = false
            if (name != null && existing.name != name) { existing.name = name; updated = true }
            if (picture != null && existing.picture != picture) { existing.picture = picture; updated = true }
            if (existing.email != email) { existing.email = email; updated = true }
            return if (updated) userRepository.save(existing) else existing
        }
        return userRepository.save(
            User(email = email, provider = AuthProvider.GOOGLE, providerId = providerId).also {
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
        // Always look up by (APPLE, sub) — sub is stable; email may be absent on subsequent logins
        val existing = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, providerId)
        if (existing != null) {
            return existing  // Returning user — email/name absent is expected; do NOT overwrite
        }

        // New user (first login) — email must be present; absent means iOS client scope misconfiguration
        val resolvedEmail = email
            ?: throw BadCredentialsException(
                "Email not present in Apple identity token on first sign-in — check iOS email scope configuration"
            )

        // Name persisted atomically in same @Transactional call (AUTH-05)
        // Apple does not provide profile pictures
        return userRepository.save(
            User(
                email = resolvedEmail,
                provider = AuthProvider.APPLE,
                providerId = providerId
            ).also { user ->
                user.name = name  // Null if iOS client didn't send givenName/familyName
            }
        )
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): User =
        userRepository.findById(id).orElseThrow { AccessDeniedException("User not found") }
}
