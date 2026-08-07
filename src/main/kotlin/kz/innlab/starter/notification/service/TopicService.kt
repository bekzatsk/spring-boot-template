package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.NotificationTopic
import kz.innlab.starter.notification.repository.DeviceTokenRepository
import kz.innlab.starter.notification.repository.NotificationTopicRepository
import kz.innlab.starter.shared.error.ForbiddenOperationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TopicService(
    private val notificationTopicRepository: NotificationTopicRepository,
    private val pushService: PushService,
    private val deviceTokenRepository: DeviceTokenRepository
) {

    @Transactional
    fun createTopic(name: String): NotificationTopic {
        if (notificationTopicRepository.existsByName(name)) {
            throw IllegalStateException("Topic '$name' already exists")
        }
        return notificationTopicRepository.save(NotificationTopic(name))
    }

    @Transactional
    fun deleteTopic(name: String) {
        val topic = notificationTopicRepository.findByName(name)
            ?: throw IllegalArgumentException("Topic '$name' not found")
        notificationTopicRepository.delete(topic)
    }

    fun validateTopicExists(name: String) {
        if (!notificationTopicRepository.existsByName(name)) {
            throw IllegalArgumentException("Topic '$name' does not exist")
        }
    }

    fun subscribe(userId: UUID, token: String, topicName: String) {
        assertTokenOwnedBy(userId, token)
        validateTopicExists(topicName)
        pushService.subscribeToTopic(listOf(token), topicName)
    }

    fun unsubscribe(userId: UUID, token: String, topicName: String) {
        assertTokenOwnedBy(userId, token)
        validateTopicExists(topicName)
        pushService.unsubscribeFromTopic(listOf(token), topicName)
    }

    // A caller may only manage topic subscriptions for their own registered devices.
    private fun assertTokenOwnedBy(userId: UUID, token: String) {
        val owned = deviceTokenRepository.findByUserId(userId).any { it.fcmToken == token }
        if (!owned) {
            throw ForbiddenOperationException("Token is not registered to the current user")
        }
    }
}
