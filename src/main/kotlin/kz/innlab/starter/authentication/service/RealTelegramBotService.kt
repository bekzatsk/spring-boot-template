package kz.innlab.starter.authentication.service

import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

/**
 * Real Telegram Bot API client. Activated by TelegramConfig when
 * `app.auth.telegram.bot-token` is configured (non-blank).
 *
 * Sends messages via POST https://api.telegram.org/bot<token>/sendMessage.
 * Errors are logged but never thrown — auth flow must not break on
 * transient Telegram outages.
 */
class RealTelegramBotService(
    private val botToken: String
) : TelegramBotService {

    companion object {
        private val logger = LoggerFactory.getLogger(RealTelegramBotService::class.java)
    }

    private val client: RestClient = RestClient.create("https://api.telegram.org")

    override fun sendMessage(chatId: Long, text: String) =
        send(mapOf("chat_id" to chatId, "text" to text))

    override fun requestContact(chatId: Long, text: String, buttonText: String) =
        send(
            mapOf(
                "chat_id" to chatId,
                "text" to text,
                "reply_markup" to mapOf(
                    "keyboard" to listOf(listOf(mapOf("text" to buttonText, "request_contact" to true))),
                    "resize_keyboard" to true,
                    "one_time_keyboard" to true
                )
            )
        )

    override fun sendMessageRemovingKeyboard(chatId: Long, text: String) =
        send(mapOf("chat_id" to chatId, "text" to text, "reply_markup" to mapOf("remove_keyboard" to true)))

    private fun send(body: Map<String, Any>) {
        val chatId = body["chat_id"]
        try {
            client.post()
                .uri("/bot{token}/sendMessage", botToken)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .toBodilessEntity()
            logger.info("[TELEGRAM] sent message to chatId={}", chatId)
        } catch (e: Exception) {
            logger.error("[TELEGRAM] send failed chatId={}: {}", chatId, e.message)
        }
    }
}
