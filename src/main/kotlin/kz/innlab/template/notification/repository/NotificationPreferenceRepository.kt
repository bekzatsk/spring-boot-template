package kz.innlab.template.notification.repository

import kz.innlab.template.notification.model.NotificationChannel
import kz.innlab.template.notification.model.NotificationPreference
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, UUID> {

    fun findByUserId(userId: UUID): List<NotificationPreference>

    fun findByUserIdAndChannel(userId: UUID, channel: NotificationChannel): NotificationPreference?
}
