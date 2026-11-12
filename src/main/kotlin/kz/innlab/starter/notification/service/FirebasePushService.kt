package kz.innlab.starter.notification.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import kz.innlab.starter.notification.model.PushTargetType
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")
class FirebasePushService : PushService {

    companion object {
        private val logger = LoggerFactory.getLogger(FirebasePushService::class.java)
    }

    // Registration tokens stay supported: deployed clients send them, and an FID is a different
    // value, not a new spelling of the token. Registrations that hold an FID go through sendToFid.
    @Suppress("DEPRECATION")
    override fun sendToToken(token: String, title: String, body: String, data: Map<String, String>): String? =
        send(Message.builder().setToken(token), title, body, data, "token")

    override fun sendToFid(fid: String, title: String, body: String, data: Map<String, String>): String? =
        send(Message.builder().setFid(fid), title, body, data, "FID")

    private fun send(
        builder: Message.Builder,
        title: String,
        body: String,
        data: Map<String, String>,
        targetKind: String
    ): String? {
        val message = builder
            .setNotification(notification(title, body))
            .putAllData(data)
            .build()

        return try {
            val messageId = FirebaseMessaging.getInstance().send(message)
            logger.info("FCM message sent to {}: {}", targetKind, messageId)
            messageId
        } catch (e: FirebaseMessagingException) {
            when (e.messagingErrorCode) {
                MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT -> {
                    logger.warn("Stale FCM {} detected: {}", targetKind, e.messagingErrorCode)
                    throw e
                }
                else -> {
                    logger.error("FCM send failed: {}", e.messagingErrorCode, e)
                    throw e
                }
            }
        }
    }

    override fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> =
        sendMulticastToTargets(tokens.map { PushTarget(it, PushTargetType.TOKEN) }, title, body, data)

    @Suppress("DEPRECATION") // addAllTokens: see sendToToken
    override fun sendMulticastToTargets(targets: List<PushTarget>, title: String, body: String, data: Map<String, String>): List<String> {
        val tokens = targets.filter { it.type == PushTargetType.TOKEN }.map { it.value }
        val fids = targets.filter { it.type == PushTargetType.FID }.map { it.value }

        val multicast = MulticastMessage.builder()
            .setNotification(notification(title, body))
            .putAllData(data)
            .addAllTokens(tokens)
            .addAllFids(fids)
            .build()

        val batchResponse = FirebaseMessaging.getInstance().sendEachForMulticast(multicast)
        logger.info("FCM multicast: {}/{} successful", batchResponse.successCount, targets.size)

        // FCM answers tokens first, then FIDs, each in the order given.
        val recipients = tokens + fids
        val staleTargets = mutableListOf<String>()
        batchResponse.responses.forEachIndexed { index, response ->
            if (!response.isSuccessful) {
                val errorCode = response.exception?.messagingErrorCode
                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    staleTargets.add(recipients[index])
                }
            }
        }

        if (staleTargets.isNotEmpty()) {
            logger.warn("Found {} stale FCM targets in multicast", staleTargets.size)
        }

        return staleTargets
    }

    private fun notification(title: String, body: String): Notification =
        Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build()

    override fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String>): String? {
        val message = Message.builder()
            .setNotification(notification(title, body))
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
