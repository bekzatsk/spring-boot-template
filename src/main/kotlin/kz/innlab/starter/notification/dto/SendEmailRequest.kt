package kz.innlab.starter.notification.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendEmailRequest(
    // One address only: a comma-separated list would fan out to several recipients
    // on providers that parse the field as an address list.
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val to: String,

    // mail_history.subject is VARCHAR(255); longer subjects failed the insert with 500.
    @field:NotBlank
    @field:Size(max = 255)
    val subject: String,

    @field:Size(max = 1_000_000)
    val textBody: String? = null,
    @field:Size(max = 1_000_000)
    val htmlBody: String? = null
)
