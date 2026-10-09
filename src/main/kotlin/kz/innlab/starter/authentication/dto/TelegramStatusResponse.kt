package kz.innlab.starter.authentication.dto

import java.time.Instant

data class TelegramStatusResponse(
    val status: String,
    val telegramConnected: Boolean,
    /** True while the bot waits for the user to share their phone number. */
    val phoneRequired: Boolean = false,
    val expiresAt: Instant
)
