package kz.innlab.starter.authentication.model

enum class TelegramSessionStatus {
    PENDING,
    /** The bot asked for the user's contact (app.auth.telegram.require-phone) and waits for it. */
    PHONE_REQUESTED,
    CODE_SENT,
    VERIFIED,
    EXPIRED
}
