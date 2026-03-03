package kz.innlab.template.notification.dto

import jakarta.validation.constraints.NotBlank

data class TopicSubscribeRequest(
    @field:NotBlank
    val token: String
)
