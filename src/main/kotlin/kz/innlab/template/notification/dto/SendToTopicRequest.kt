package kz.innlab.template.notification.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendToTopicRequest(
    @field:NotBlank
    val topic: String,

    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:NotBlank
    val body: String,

    val data: Map<String, String> = emptyMap()
)
