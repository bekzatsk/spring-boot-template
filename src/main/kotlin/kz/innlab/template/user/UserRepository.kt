package kz.innlab.template.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
