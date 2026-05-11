package kz.innlab.starter.authentication.dto

import java.time.Instant

data class TelegramInitResponse(
    val sessionId: String,
    val botUrl: String,
    val botUsername: String,
    val expiresAt: Instant
)
