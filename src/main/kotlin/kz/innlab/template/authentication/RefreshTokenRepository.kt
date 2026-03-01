package kz.innlab.template.authentication

import kz.innlab.template.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :tokenHash")
    fun findByTokenHash(@Param("tokenHash") tokenHash: String): RefreshToken?

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    fun deleteAllByUser(@Param("user") user: User)

    fun findByReplacedByTokenHash(replacedByTokenHash: String): RefreshToken?
}
