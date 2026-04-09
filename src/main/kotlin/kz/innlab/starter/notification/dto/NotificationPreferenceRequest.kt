package kz.innlab.starter.notification.dto

data class NotificationPreferenceRequest(
    val push: Boolean? = null,
    val email: Boolean? = null
)
