package kz.innlab.starter.notification.dto

data class NotificationPreferenceResponse(
    val push: Boolean,
    val email: Boolean
)
