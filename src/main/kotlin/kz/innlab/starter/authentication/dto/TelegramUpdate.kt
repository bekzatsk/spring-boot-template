package kz.innlab.starter.authentication.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Minimal typed view of a Telegram Bot API Update. Only the fields this starter reads are
 * declared; everything else is ignored.
 *
 * Replaces nested Map<String, Any> parsing with unchecked casts, which risked a
 * ClassCastException on any payload shape that differed from the expected one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUpdate(
    val message: TelegramMessage? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramMessage(
    val text: String? = null,
    val chat: TelegramChat? = null,
    val from: TelegramUser? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChat(
    // Nullable on purpose: a malformed payload must be ignored, not answered with an error.
    val id: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUser(
    val id: Long? = null,
    val username: String? = null
)
