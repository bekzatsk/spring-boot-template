package kz.innlab.starter.authentication.repository

import kz.innlab.starter.authentication.model.TelegramAuthSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface TelegramAuthSessionRepository : JpaRepository<TelegramAuthSession, UUID> {

    fun findBySessionId(sessionId: String): TelegramAuthSession?

    fun countByIpAddressAndCreatedAtAfter(ipAddress: String, after: Instant): Long

    fun countByTelegramUserIdAndCreatedAtAfter(telegramUserId: Long, after: Instant): Long

    @Modifying
    @Query("DELETE FROM TelegramAuthSession t WHERE t.expiresAt < :now")
    fun deleteExpired(@Param("now") now: Instant)
}
