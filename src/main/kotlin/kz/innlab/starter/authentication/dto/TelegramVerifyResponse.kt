package kz.innlab.starter.authentication.dto

data class TelegramVerifyResponse(
    val verified: Boolean,
    val telegramUserId: Long? = null,
    val telegramUsername: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val error: String? = null,
    val attemptsLeft: Int? = null,
    val message: String? = null
)
