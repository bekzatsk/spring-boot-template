package kz.innlab.starter.notification.dto

import jakarta.validation.constraints.Size
import jakarta.validation.constraints.NotBlank

data class RegisterTokenRequest(
    @field:NotBlank
    val platform: String,

    // FCM tokens are ~160-200 characters. Unbounded, a long one broke the unique index on
    // PostgreSQL (index rows are capped near 2.7 KB) and answered 500.
    @field:NotBlank
    @field:Size(max = 512)
    val fcmToken: String,

    @field:NotBlank
    @field:Size(max = 255)
    val deviceId: String
)
