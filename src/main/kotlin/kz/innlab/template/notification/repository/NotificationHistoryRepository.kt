package kz.innlab.template.notification.repository

import kz.innlab.template.notification.model.NotificationHistory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NotificationHistoryRepository : JpaRepository<NotificationHistory, UUID> {

    @Query("SELECT n FROM NotificationHistory n WHERE n.userId = :userId AND n.id < :cursor ORDER BY n.id DESC")
    fun findByUserIdBeforeCursor(
        @Param("userId") userId: UUID,
        @Param("cursor") cursor: UUID,
        pageable: Pageable
    ): List<NotificationHistory>

    @Query("SELECT n FROM NotificationHistory n WHERE n.userId = :userId ORDER BY n.id DESC")
    fun findByUserIdLatest(
        @Param("userId") userId: UUID,
        pageable: Pageable
    ): List<NotificationHistory>
}
