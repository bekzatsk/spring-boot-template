package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.NotificationChannel
import kz.innlab.starter.notification.model.NotificationHistory
import kz.innlab.starter.notification.model.NotificationType
import kz.innlab.starter.notification.repository.DeviceTokenRepository
import kz.innlab.starter.notification.repository.NotificationHistoryRepository
import kz.innlab.starter.shared.error.ForbiddenOperationException
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationDispatcher: NotificationDispatcher,
    private val notificationPreferenceService: NotificationPreferenceService,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val afterCommitRunner: AfterCommitRunner
) {

    @Transactional
    fun sendToToken(userId: UUID, token: String, title: String, body: String, data: Map<String, String>): UUID? {
        assertTokensOwnedBy(userId, listOf(token))
        if (!notificationPreferenceService.isChannelEnabled(userId, NotificationChannel.PUSH)) {
            return null
        }

        val history = NotificationHistory(userId, NotificationType.SINGLE, token, title, body)
        history.data = data.toString()
        notificationHistoryRepository.save(history)

        // After commit: the @Async dispatcher re-reads the history row by id and must not race
        // an uncommitted insert.
        afterCommitRunner.run { notificationDispatcher.dispatchToToken(history.id, token, title, body, data) }
        return history.id
    }

    @Transactional
    fun sendMulticast(userId: UUID, tokens: List<String>, title: String, body: String, data: Map<String, String>): UUID? {
        assertTokensOwnedBy(userId, tokens)
        if (!notificationPreferenceService.isChannelEnabled(userId, NotificationChannel.PUSH)) {
            return null
        }

        require(tokens.size <= 500) { "Maximum 500 tokens per multicast" }

        val history = NotificationHistory(userId, NotificationType.MULTICAST, tokens.joinToString(","), title, body)
        history.data = data.toString()
        notificationHistoryRepository.save(history)

        afterCommitRunner.run { notificationDispatcher.dispatchMulticast(history.id, tokens, title, body, data) }
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

        afterCommitRunner.run { notificationDispatcher.dispatchToTopic(history.id, topic, title, body, data) }
        return history.id
    }

    /**
     * A caller may only push to FCM tokens registered as their own devices; otherwise any
     * authenticated user could send app-branded notifications to arbitrary victims.
     */
    private fun assertTokensOwnedBy(userId: UUID, tokens: Collection<String>) {
        val owned = deviceTokenRepository.findByUserId(userId).mapTo(mutableSetOf()) { it.fcmToken }
        if (tokens.any { it !in owned }) {
            throw ForbiddenOperationException("One or more tokens are not registered to the current user")
        }
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
