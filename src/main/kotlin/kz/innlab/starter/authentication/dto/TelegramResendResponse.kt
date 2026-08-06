package kz.innlab.starter.authentication.dto

/** Result of re-sending a Telegram login code. [cooldown] is the enforced wait in seconds. */
data class TelegramResendResponse(
    val sent: Boolean,
    val cooldown: Long,
    val message: String
)
