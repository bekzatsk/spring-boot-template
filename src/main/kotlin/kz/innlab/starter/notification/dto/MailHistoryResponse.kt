package kz.innlab.starter.notification.dto

import kz.innlab.starter.notification.model.MailHistory
import java.time.Instant
import java.util.UUID

data class MailHistoryResponse(
    val id: UUID,
    val toAddress: String,
    val subject: String,
    val hasAttachments: Boolean,
    val status: String,
    val attempts: Int,
    val createdAt: Instant
) {
    companion object {
        fun from(history: MailHistory): MailHistoryResponse = MailHistoryResponse(
            id = history.id,
            toAddress = history.toAddress,
            subject = history.subject,
            hasAttachments = history.hasAttachments,
            status = history.status.name,
            attempts = history.attempts,
            createdAt = history.createdAt
        )
    }
}
