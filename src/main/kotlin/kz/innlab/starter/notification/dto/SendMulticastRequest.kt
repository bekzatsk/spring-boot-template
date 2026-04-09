package kz.innlab.starter.notification.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class SendMulticastRequest(
    @field:NotEmpty
    @field:Size(max = 500)
    val tokens: List<String>,

    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:NotBlank
    val body: String,

    val data: Map<String, String> = emptyMap()
)
