package kz.innlab.starter.authentication.service

import org.slf4j.LoggerFactory

/**
 * Fallback when no bot token is configured. Logs that a message would have gone out, never its
 * text: the text carries the login code, and a log line is not a delivery channel. In development
 * use `app.auth.telegram.dev-code` to know the code.
 */
class ConsoleTelegramBotService : TelegramBotService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsoleTelegramBotService::class.java)
    }

    override fun sendMessage(chatId: Long, text: String) {
        logger.info("[TELEGRAM] Message to chatId={} not sent: no bot token configured ({} chars, not logged)", chatId, text.length)
    }
}
