package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank

data class TelegramResendRequest(
    @field:NotBlank(message = "Session ID is required")
    val sessionId: String
)
