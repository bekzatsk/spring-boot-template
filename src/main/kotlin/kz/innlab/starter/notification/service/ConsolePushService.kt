package kz.innlab.starter.notification.service

import org.slf4j.LoggerFactory

/**
 * Fallback when Firebase is not configured. Logs that a push would have gone out without the
 * device token or the message content: both belong to a user, and the log is not theirs.
 */
class ConsolePushService : PushService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsolePushService::class.java)
    }

    override fun sendToToken(token: String, title: String, body: String, data: Map<String, String>): String? {
        logger.info("[PUSH] sendToToken - token: {}…, {} data keys (content not logged)", token.take(6), data.size)
        return "console-message-id"
    }

    override fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> {
        logger.info("[PUSH] sendMulticast - tokens: {}, {} data keys (content not logged)", tokens.size, data.size)
        return emptyList()
    }

    override fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String>): String? {
        logger.info("[PUSH] sendToTopic - topic: {}, {} data keys (content not logged)", topic, data.size)
        return "console-message-id"
    }

    override fun subscribeToTopic(tokens: List<String>, topic: String) {
        logger.info("[PUSH] subscribeToTopic - tokens: {}, topic: {}", tokens.size, topic)
    }

    override fun unsubscribeFromTopic(tokens: List<String>, topic: String) {
        logger.info("[PUSH] unsubscribeFromTopic - tokens: {}, topic: {}", tokens.size, topic)
    }
}
