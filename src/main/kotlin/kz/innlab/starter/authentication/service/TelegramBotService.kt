package kz.innlab.starter.authentication.service

interface TelegramBotService {
    fun sendMessage(chatId: Long, text: String)
}
