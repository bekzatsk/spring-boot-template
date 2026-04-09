package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.NotificationChannel
import kz.innlab.starter.notification.model.NotificationHistory
import kz.innlab.starter.notification.model.NotificationType
import kz.innlab.starter.notification.repository.NotificationHistoryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationDispatcher: NotificationDispatcher,
    private val notificationPreferenceService: NotificationPreferenceService
) {

    @Transactional
    fun sendToToken(userId: UUID, token: String, title: String, body: String, data: Map<String, String>): UUID? {
        if (!notificationPreferenceService.isChannelEnabled(userId, NotificationChannel.PUSH)) {
            return null
        }

        val history = NotificationHistory(userId, NotificationType.SINGLE, token, title, body)
        history.data = data.toString()
        notificationHistoryRepository.save(history)

        notificationDispatcher.dispatchToToken(history.id, token, title, body, data)
        return history.id
    }

    @Transactional
    fun sendMulticast(userId: UUID, tokens: List<String>, title: String, body: String, data: Map<String, String>): UUID? {
        if (!notificationPreferenceService.isChannelEnabled(userId, NotificationChannel.PUSH)) {
            return null
        }

        require(tokens.size <= 500) { "Maximum 500 tokens per multicast" }

        val history = NotificationHistory(userId, NotificationType.MULTICAST, tokens.joinToString(","), title, body)
        history.data = data.toString()
        notificationHistoryRepository.save(history)

        notificationDispatcher.dispatchMulticast(history.id, tokens, title, body, data)
        return history.id
    }

    @Transactional
    fun sendToTopic(userId: UUID, topic: String, title: String, body: String, data: Map<String, String>): UUID? {
        if (!notificationPreferenceService.isChannelEnabled(userId, NotificationChannel.PUSH)) {
            return null
        }

        val history = NotificationHistory(userId, NotificationType.TOPIC, topic, title, body)
        history.data = data.toString()
        notificationHistoryRepository.save(history)

        notificationDispatcher.dispatchToTopic(history.id, topic, title, body, data)
        return history.id
    }

    fun getHistory(userId: UUID, cursor: UUID?, size: Int): List<NotificationHistory> {
        val pageable = PageRequest.of(0, size)
        return if (cursor == null) {
            notificationHistoryRepository.findByUserIdLatest(userId, pageable)
        } else {
            notificationHistoryRepository.findByUserIdBeforeCursor(userId, cursor, pageable)
        }
    }
}
