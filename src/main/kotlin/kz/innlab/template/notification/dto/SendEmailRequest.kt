package kz.innlab.template.notification.dto

import jakarta.validation.constraints.NotBlank

data class SendEmailRequest(
    @field:NotBlank
    val to: String,

    @field:NotBlank
    val subject: String,

    val textBody: String? = null,
    val htmlBody: String? = null
)
