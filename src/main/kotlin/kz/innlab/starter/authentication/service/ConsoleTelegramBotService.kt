package kz.innlab.starter.authentication.service

import org.slf4j.LoggerFactory

class ConsoleTelegramBotService : TelegramBotService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsoleTelegramBotService::class.java)
    }

    override fun sendMessage(chatId: Long, text: String) {
        logger.info("[TELEGRAM] Sending message to chatId={}: {}", chatId, text)
    }
}
