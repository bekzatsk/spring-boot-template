package kz.innlab.template.notification.dto

import kz.innlab.template.notification.service.InboxMessage
import java.time.Instant

data class InboxMessageResponse(
    val messageNumber: Int,
    val subject: String,
    val from: String,
    val date: Instant?,
    val isRead: Boolean,
    val hasAttachments: Boolean
) {
    companion object {
        fun from(msg: InboxMessage): InboxMessageResponse = InboxMessageResponse(
            messageNumber = msg.messageNumber,
            subject = msg.subject,
            from = msg.from,
            date = msg.date,
            isRead = msg.isRead,
            hasAttachments = msg.hasAttachments
        )
    }
}
