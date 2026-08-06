package kz.innlab.starter.notification.dto

import java.util.UUID

/** Accepted-for-delivery handle; look the outcome up in mail history. */
data class MailSendResponse(
    val mailId: UUID
)
