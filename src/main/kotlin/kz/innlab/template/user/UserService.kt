package kz.innlab.template.user

import org.springframework.security.access.AccessDeniedException
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
    ): User =
        userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
            ?: userRepository.save(
                User(email = email, provider = AuthProvider.GOOGLE, providerId = providerId).also {
                    it.name = name
                    it.picture = picture
                }
            )

    @Transactional(readOnly = true)
    fun findById(id: UUID): User =
        userRepository.findById(id).orElseThrow { AccessDeniedException("User not found") }
}
