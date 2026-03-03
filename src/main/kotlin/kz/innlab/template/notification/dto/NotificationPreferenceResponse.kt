package kz.innlab.template.notification.dto

data class NotificationPreferenceResponse(
    val push: Boolean,
    val email: Boolean
)
