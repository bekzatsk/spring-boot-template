package kz.innlab.template.notification.service

import kz.innlab.template.notification.model.NotificationChannel
import kz.innlab.template.notification.model.NotificationPreference
import kz.innlab.template.notification.repository.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class NotificationPreferenceService(
    private val notificationPreferenceRepository: NotificationPreferenceRepository
) {

    fun isChannelEnabled(userId: UUID, channel: NotificationChannel): Boolean {
        val pref = notificationPreferenceRepository.findByUserIdAndChannel(userId, channel)
        return pref?.enabled ?: true // opt-out model: default enabled when no row exists
    }

    fun getPreferences(userId: UUID): Map<NotificationChannel, Boolean> {
        val prefs = notificationPreferenceRepository.findByUserId(userId)
        return NotificationChannel.entries.associateWith { channel ->
            prefs.find { it.channel == channel }?.enabled ?: true
        }
    }

    @Transactional
    fun updatePreferences(userId: UUID, updates: Map<NotificationChannel, Boolean>) {
        for ((channel, enabled) in updates) {
            val existing = notificationPreferenceRepository.findByUserIdAndChannel(userId, channel)
            if (existing != null) {
                existing.enabled = enabled
                existing.updatedAt = Instant.now()
                notificationPreferenceRepository.save(existing)
            } else {
                val preference = NotificationPreference(userId, channel, enabled)
                notificationPreferenceRepository.save(preference)
            }
        }
    }
}
