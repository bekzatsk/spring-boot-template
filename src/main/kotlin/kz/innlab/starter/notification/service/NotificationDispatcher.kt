package kz.innlab.starter.notification.service

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import kz.innlab.starter.config.AsyncConfig
import kz.innlab.starter.notification.model.NotificationStatus
import kz.innlab.starter.notification.model.PushTargetType
import kz.innlab.starter.notification.repository.NotificationHistoryRepository
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

    @Async(AsyncConfig.STARTER_EXECUTOR)
    @Transactional
    fun dispatchToToken(historyId: UUID, target: PushTarget, title: String, body: String, data: Map<String, String>) {
        val history = notificationHistoryRepository.findById(historyId).orElseThrow()
        try {
            when (target.type) {
                PushTargetType.TOKEN -> pushService.sendToToken(target.value, title, body, data)
                PushTargetType.FID -> pushService.sendToFid(target.value, title, body, data)
            }
            history.status = NotificationStatus.SENT
        } catch (e: Exception) {
            logger.error("Failed to dispatch notification {}: {}", historyId, e.message)
            if (e is FirebaseMessagingException) {
                val errorCode = e.messagingErrorCode
                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    deviceTokenService.cleanupStaleTokens(listOf(target.value))
                }
            }
            history.status = NotificationStatus.FAILED
        } finally {
            notificationHistoryRepository.save(history)
        }
    }

    @Async(AsyncConfig.STARTER_EXECUTOR)
    @Transactional
    fun dispatchMulticast(historyId: UUID, targets: List<PushTarget>, title: String, body: String, data: Map<String, String>) {
        val history = notificationHistoryRepository.findById(historyId).orElseThrow()
        try {
            // Token-only sends keep going through sendMulticast, so a PushService written before
            // FIDs existed handles them as it always did.
            val staleTokens = if (targets.all { it.type == PushTargetType.TOKEN }) {
                pushService.sendMulticast(targets.map { it.value }, title, body, data)
            } else {
                pushService.sendMulticastToTargets(targets, title, body, data)
            }
            deviceTokenService.cleanupStaleTokens(staleTokens)
            history.status = NotificationStatus.SENT
        } catch (e: Exception) {
            logger.error("Failed to dispatch multicast notification {}: {}", historyId, e.message)
            history.status = NotificationStatus.FAILED
        } finally {
            notificationHistoryRepository.save(history)
        }
    }

    @Async(AsyncConfig.STARTER_EXECUTOR)
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
