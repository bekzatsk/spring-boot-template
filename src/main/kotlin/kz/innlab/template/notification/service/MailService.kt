package kz.innlab.template.notification.service

import java.util.UUID

interface MailService {
    fun send(
        to: String,
        subject: String,
        textBody: String? = null,
        htmlBody: String? = null,
        attachments: List<EmailAttachment> = emptyList()
    )

    fun sendEmail(
        userId: UUID,
        to: String,
        subject: String,
        textBody: String? = null,
        htmlBody: String? = null,
        attachments: List<EmailAttachment> = emptyList()
    ): UUID
}

data class EmailAttachment(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray
)
