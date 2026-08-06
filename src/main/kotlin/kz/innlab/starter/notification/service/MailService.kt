package kz.innlab.starter.notification.service

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

/**
 * Not a data class: the generated equals/hashCode would compare [bytes] by reference, so two
 * attachments with identical content would be unequal and hash differently.
 */
class EmailAttachment(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmailAttachment) return false
        return filename == other.filename &&
            contentType == other.contentType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
