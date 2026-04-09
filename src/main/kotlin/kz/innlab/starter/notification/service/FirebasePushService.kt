package kz.innlab.starter.notification.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")
class FirebasePushService : PushService {

    companion object {
        private val logger = LoggerFactory.getLogger(FirebasePushService::class.java)
    }

    override fun sendToToken(token: String, title: String, body: String, data: Map<String, String>): String? {
        val notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build()

        val message = Message.builder()
            .setNotification(notification)
            .putAllData(data)
            .setToken(token)
            .build()

        return try {
            val messageId = FirebaseMessaging.getInstance().send(message)
            logger.info("FCM message sent to token: {}", messageId)
            messageId
        } catch (e: FirebaseMessagingException) {
            when (e.messagingErrorCode) {
                MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT -> {
                    logger.warn("Stale FCM token detected: {}", e.messagingErrorCode)
                    throw e
                }
                else -> {
                    logger.error("FCM send failed: {}", e.messagingErrorCode, e)
                    throw e
                }
            }
        }
    }

    override fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> {
        val notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build()

        val multicast = MulticastMessage.builder()
            .setNotification(notification)
            .putAllData(data)
            .addAllTokens(tokens)
            .build()

        val batchResponse = FirebaseMessaging.getInstance().sendEachForMulticast(multicast)
        logger.info("FCM multicast: {}/{} successful", batchResponse.successCount, tokens.size)

        val staleTokens = mutableListOf<String>()
        batchResponse.responses.forEachIndexed { index, response ->
            if (!response.isSuccessful) {
                val errorCode = response.exception?.messagingErrorCode
                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    staleTokens.add(tokens[index])
                }
            }
        }

        if (staleTokens.isNotEmpty()) {
            logger.warn("Found {} stale FCM tokens in multicast", staleTokens.size)
        }

        return staleTokens
    }

    override fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String>): String? {
        val notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build()

        val message = Message.builder()
            .setNotification(notification)
            .putAllData(data)
            .setTopic(topic)
            .build()

        val messageId = FirebaseMessaging.getInstance().send(message)
        logger.info("FCM topic message sent to '{}': {}", topic, messageId)
        return messageId
    }

    override fun subscribeToTopic(tokens: List<String>, topic: String) {
        val response = FirebaseMessaging.getInstance().subscribeToTopic(tokens, topic)
        logger.info("FCM topic subscribe '{}': {} success, {} failures", topic, response.successCount, response.failureCount)
    }

    override fun unsubscribeFromTopic(tokens: List<String>, topic: String) {
        val response = FirebaseMessaging.getInstance().unsubscribeFromTopic(tokens, topic)
        logger.info("FCM topic unsubscribe '{}': {} success, {} failures", topic, response.successCount, response.failureCount)
    }
}
