package kz.innlab.template.notification.service

import kz.innlab.template.notification.model.NotificationTopic
import kz.innlab.template.notification.repository.NotificationTopicRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TopicService(
    private val notificationTopicRepository: NotificationTopicRepository,
    private val pushService: PushService
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

    fun subscribe(token: String, topicName: String) {
        validateTopicExists(topicName)
        pushService.subscribeToTopic(listOf(token), topicName)
    }

    fun unsubscribe(token: String, topicName: String) {
        validateTopicExists(topicName)
        pushService.unsubscribeFromTopic(listOf(token), topicName)
    }
}
