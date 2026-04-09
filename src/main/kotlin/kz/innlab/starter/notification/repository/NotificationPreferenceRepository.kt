package kz.innlab.starter.notification.repository

import kz.innlab.starter.notification.model.NotificationChannel
import kz.innlab.starter.notification.model.NotificationPreference
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, UUID> {

    fun findByUserId(userId: UUID): List<NotificationPreference>

    fun findByUserIdAndChannel(userId: UUID, channel: NotificationChannel): NotificationPreference?
}
