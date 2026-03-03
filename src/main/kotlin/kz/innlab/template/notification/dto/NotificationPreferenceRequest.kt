package kz.innlab.template.notification.dto

data class NotificationPreferenceRequest(
    val push: Boolean? = null,
    val email: Boolean? = null
)
