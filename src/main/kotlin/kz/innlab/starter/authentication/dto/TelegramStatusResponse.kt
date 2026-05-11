package kz.innlab.starter.authentication.dto

import java.time.Instant

data class TelegramStatusResponse(
    val status: String,
    val telegramConnected: Boolean,
    val expiresAt: Instant
)
