package kz.innlab.template.user.repository

import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
