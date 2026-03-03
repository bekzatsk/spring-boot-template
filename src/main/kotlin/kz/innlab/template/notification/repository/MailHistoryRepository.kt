package kz.innlab.template.notification.repository

import kz.innlab.template.notification.model.MailHistory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MailHistoryRepository : JpaRepository<MailHistory, UUID> {

    @Query("SELECT m FROM MailHistory m WHERE m.userId = :userId AND m.id < :cursor ORDER BY m.id DESC")
    fun findByUserIdBeforeCursor(
        @Param("userId") userId: UUID,
        @Param("cursor") cursor: UUID,
        pageable: Pageable
    ): List<MailHistory>

    @Query("SELECT m FROM MailHistory m WHERE m.userId = :userId ORDER BY m.id DESC")
    fun findByUserIdLatest(
        @Param("userId") userId: UUID,
        pageable: Pageable
    ): List<MailHistory>
}
