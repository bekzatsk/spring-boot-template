package kz.innlab.template.notification.service

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import kz.innlab.template.notification.model.NotificationStatus
import kz.innlab.template.notification.repository.NotificationHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationDispatcher(
    private val pushService: PushService,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val deviceTokenService: DeviceTokenService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(NotificationDispatcher::class.java)
    }

    @Async
    @Transactional
    fun dispatchToToken(historyId: UUID, token: String, title: String, body: String, data: Map<String, String>) {
        val history = notificationHistoryRepository.findById(historyId).orElseThrow()
        try {
            pushService.sendToToken(token, title, body, data)
            history.status = NotificationStatus.SENT
        } catch (e: Exception) {
            logger.error("Failed to dispatch notification {}: {}", historyId, e.message)
            if (e is FirebaseMessagingException) {
                val errorCode = e.messagingErrorCode
                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    deviceTokenService.cleanupStaleTokens(listOf(token))
                }
            }
            history.status = NotificationStatus.FAILED
        } finally {
            notificationHistoryRepository.save(history)
        }
    }

    @Async
    @Transactional
    fun dispatchMulticast(historyId: UUID, tokens: List<String>, title: String, body: String, data: Map<String, String>) {
        val history = notificationHistoryRepository.findById(historyId).orElseThrow()
        try {
            val staleTokens = pushService.sendMulticast(tokens, title, body, data)
            deviceTokenService.cleanupStaleTokens(staleTokens)
            history.status = NotificationStatus.SENT
        } catch (e: Exception) {
            logger.error("Failed to dispatch multicast notification {}: {}", historyId, e.message)
            history.status = NotificationStatus.FAILED
        } finally {
            notificationHistoryRepository.save(history)
        }
    }

    @Async
    @Transactional
    fun dispatchToTopic(historyId: UUID, topic: String, title: String, body: String, data: Map<String, String>) {
        val history = notificationHistoryRepository.findById(historyId).orElseThrow()
        try {
            pushService.sendToTopic(topic, title, body, data)
            history.status = NotificationStatus.SENT
        } catch (e: Exception) {
            logger.error("Failed to dispatch topic notification {}: {}", historyId, e.message)
            history.status = NotificationStatus.FAILED
        } finally {
            notificationHistoryRepository.save(history)
        }
    }
}
