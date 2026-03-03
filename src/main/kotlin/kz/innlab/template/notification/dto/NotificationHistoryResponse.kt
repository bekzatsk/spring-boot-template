package kz.innlab.template.notification.dto

import kz.innlab.template.notification.model.NotificationHistory
import java.time.Instant
import java.util.UUID

data class NotificationHistoryResponse(
    val id: UUID,
    val type: String,
    val recipient: String,
    val title: String,
    val body: String,
    val status: String,
    val createdAt: Instant
) {
    companion object {
        fun from(history: NotificationHistory): NotificationHistoryResponse = NotificationHistoryResponse(
            id = history.id,
            type = history.type.name,
            recipient = history.recipient,
            title = history.title,
            body = history.body,
            status = history.status.name,
            createdAt = history.createdAt
        )
    }
}
