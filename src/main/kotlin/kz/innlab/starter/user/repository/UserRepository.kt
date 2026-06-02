package kz.innlab.starter.user.repository

import kz.innlab.starter.user.model.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    @Query(
        """
        SELECT u FROM User u
        WHERE :q IS NULL OR :q = ''
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(u.name, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           OR COALESCE(u.phone, '') LIKE CONCAT('%', :q, '%')
        """
    )
    fun search(@Param("q") query: String?, pageable: Pageable): Page<User>

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r = kz.innlab.starter.user.model.Role.ADMIN")
    fun countAdmins(): Long
}
