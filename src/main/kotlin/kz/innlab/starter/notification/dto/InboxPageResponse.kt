package kz.innlab.starter.notification.dto

import kz.innlab.starter.notification.service.InboxPage

/** A page of IMAP inbox headers. */
data class InboxPageResponse(
    val messages: List<InboxMessageResponse>,
    val total: Int,
    val offset: Int,
    val size: Int
) {
    companion object {
        fun from(page: InboxPage): InboxPageResponse = InboxPageResponse(
            messages = page.messages.map { InboxMessageResponse.from(it) },
            total = page.total,
            offset = page.offset,
            size = page.size
        )
    }
}
