package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Hidden
import kz.innlab.starter.authentication.dto.TelegramUpdate
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
        @RequestBody body: TelegramUpdate,
        @RequestHeader("X-Telegram-Bot-Api-Secret-Token", required = false) secretToken: String?
    ): ResponseEntity<Void> {
        // Fail closed: with no secret configured this public endpoint would accept forged updates
        // from anyone, letting an attacker poison pending login sessions. 200 is returned in all
        // reject paths so Telegram does not retry-storm, but the update is dropped.
        if (webhookSecret.isBlank()) {
            logger.error(
                "Telegram webhook rejected: app.auth.telegram.webhook-secret is not configured. " +
                    "Set TELEGRAM_WEBHOOK_SECRET to enable webhook processing."
            )
            return ResponseEntity.ok().build()
        }
        if (secretToken != webhookSecret) {
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

    private fun processUpdate(update: TelegramUpdate) {
        val message = update.message ?: return
        val text = message.text ?: return
        val chatId = message.chat?.id ?: return
        val from = message.from ?: return

        val telegramUserId = from.id ?: return
        val telegramUsername = from.username

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
