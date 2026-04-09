package kz.innlab.starter.notification.dto

import kz.innlab.starter.notification.service.EmailMessage
import java.time.Instant

data class EmailMessageResponse(
    val messageNumber: Int,
    val subject: String,
    val from: String,
    val to: List<String>,
    val date: Instant?,
    val textBody: String?,
    val htmlBody: String?,
    val attachments: List<AttachmentMetaResponse>,
    val isRead: Boolean
) {
    data class AttachmentMetaResponse(
        val filename: String,
        val contentType: String,
        val size: Int
    )

    companion object {
        fun from(msg: EmailMessage): EmailMessageResponse = EmailMessageResponse(
            messageNumber = msg.messageNumber,
            subject = msg.subject,
            from = msg.from,
            to = msg.to,
            date = msg.date,
            textBody = msg.textBody,
            htmlBody = msg.htmlBody,
            attachments = msg.attachments.map {
                AttachmentMetaResponse(it.filename, it.contentType, it.size)
            },
            isRead = msg.isRead
        )
    }
}
