package kz.innlab.starter.user.repository

import kz.innlab.starter.user.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    fun findByPhone(phone: String): User?

    @Query("SELECT u FROM User u JOIN u.providerIds pid WHERE KEY(pid) = 'APPLE' AND VALUE(pid) = :sub")
    fun findByAppleProviderId(@Param("sub") sub: String): User?

    fun findByTelegramUserId(telegramUserId: Long): User?
}
