package kz.innlab.starter.authentication.model

enum class TelegramSessionStatus {
    PENDING,
    CODE_SENT,
    VERIFIED,
    EXPIRED
}
