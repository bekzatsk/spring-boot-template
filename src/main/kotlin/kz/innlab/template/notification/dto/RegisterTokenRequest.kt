package kz.innlab.template.notification.dto

import jakarta.validation.constraints.NotBlank

data class RegisterTokenRequest(
    @field:NotBlank
    val platform: String,

    @field:NotBlank
    val fcmToken: String,

    @field:NotBlank
    val deviceId: String
)
