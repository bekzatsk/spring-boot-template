package kz.innlab.starter.notification.service

import org.slf4j.LoggerFactory

class ConsolePushService : PushService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsolePushService::class.java)
    }

    override fun sendToToken(token: String, title: String, body: String, data: Map<String, String>): String? {
        logger.info("[PUSH] sendToToken - token: {}, title: {}, body: {}, data: {}", token, title, body, data)
        return "console-message-id"
    }

    override fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> {
        logger.info("[PUSH] sendMulticast - tokens: {}, title: {}, body: {}, data: {}", tokens.size, title, body, data)
        return emptyList()
    }

    override fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String>): String? {
        logger.info("[PUSH] sendToTopic - topic: {}, title: {}, body: {}, data: {}", topic, title, body, data)
        return "console-message-id"
    }

    override fun subscribeToTopic(tokens: List<String>, topic: String) {
        logger.info("[PUSH] subscribeToTopic - tokens: {}, topic: {}", tokens.size, topic)
    }

    override fun unsubscribeFromTopic(tokens: List<String>, topic: String) {
        logger.info("[PUSH] unsubscribeFromTopic - tokens: {}, topic: {}", tokens.size, topic)
    }
}
