package kz.innlab.starter.notification.dto

import jakarta.validation.constraints.NotBlank

data class TopicSubscribeRequest(
    @field:NotBlank
    val token: String
)
