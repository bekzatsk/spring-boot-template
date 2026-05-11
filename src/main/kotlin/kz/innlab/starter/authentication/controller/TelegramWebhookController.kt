package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Hidden
import kz.innlab.starter.authentication.service.TelegramAuthService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Hidden
@RequestMapping("/telegram")
@ConditionalOnProperty(name = ["app.auth.telegram.enabled"], havingValue = "true")
class TelegramWebhookController(
    private val telegramAuthService: TelegramAuthService,
    @Value("\${app.auth.telegram.webhook-secret:}") private val webhookSecret: String
) {

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramWebhookController::class.java)
    }

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody body: Map<String, Any>,
        @RequestHeader("X-Telegram-Bot-Api-Secret-Token", required = false) secretToken: String?
    ): ResponseEntity<Void> {
        if (webhookSecret.isNotBlank() && secretToken != webhookSecret) {
            logger.warn("Telegram webhook request with invalid secret token")
            return ResponseEntity.ok().build()
        }

        try {
            processUpdate(body)
        } catch (e: Exception) {
            logger.error("Error processing Telegram webhook: {}", e.message, e)
        }

        return ResponseEntity.ok().build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun processUpdate(update: Map<String, Any>) {
        val message = update["message"] as? Map<String, Any> ?: return
        val text = message["text"] as? String ?: return
        val chat = message["chat"] as? Map<String, Any> ?: return
        val from = message["from"] as? Map<String, Any> ?: return

        val chatId = (chat["id"] as Number).toLong()
        val telegramUserId = (from["id"] as Number).toLong()
        val telegramUsername = from["username"] as? String

        when {
            text.startsWith("/start ") -> {
                val sessionId = text.removePrefix("/start ").trim()
                if (sessionId.isNotBlank()) {
                    telegramAuthService.handleWebhookStart(sessionId, telegramUserId, telegramUsername, chatId)
                } else {
                    telegramAuthService.handleWebhookDefault(chatId)
                }
            }
            text == "/start" -> telegramAuthService.handleWebhookDefault(chatId)
            text == "/help" -> telegramAuthService.handleWebhookHelp(chatId)
            else -> telegramAuthService.handleWebhookDefault(chatId)
        }
    }
}
