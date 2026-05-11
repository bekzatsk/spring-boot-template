package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TelegramVerifyRequest(
    @field:NotBlank(message = "Session ID is required")
    val sessionId: String,

    @field:NotBlank(message = "Verification code is required")
    @field:Size(min = 6, max = 6, message = "Code must be 6 digits")
    val code: String
)
