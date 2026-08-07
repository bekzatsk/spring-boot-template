package kz.innlab.starter.config

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** Telegram login flow: bot identity, session lifetime and the anti-abuse limits. */
@Validated
@ConfigurationProperties(prefix = "app.auth.telegram")
data class TelegramAuthProperties(
    val enabled: Boolean = false,
    val botToken: String = "",
    val botUsername: String = "",
    /** Blank disables webhook processing entirely — see TelegramWebhookController. */
    val webhookSecret: String = "",
    @field:Positive val sessionTtlSeconds: Long = 300,
    @field:Positive val codeLength: Int = 6,
    @field:Positive val maxAttempts: Int = 3,
    @field:Positive val resendCooldownSeconds: Long = 60,
    @field:Positive val maxResendsPerSession: Int = 3,
    @field:Positive val maxSessionsPerIpPerHour: Int = 5,
    @field:Positive val maxSessionsPerTelegramUserPerHour: Int = 3,
    /** Only honour X-Forwarded-For when a trusted reverse proxy sets it. */
    val trustForwardedHeaders: Boolean = false,
    val devCode: String = ""
)
