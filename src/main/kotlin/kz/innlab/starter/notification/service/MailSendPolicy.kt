package kz.innlab.starter.notification.service

import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.notification.repository.MailHistoryRepository
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

/**
 * Abuse controls for the authenticated mail-send endpoints. The endpoints let a user send mail
 * from the application's own domain, so without a per-user quota and attachment caps a single
 * account can burn the sending reputation or push arbitrarily large payloads through SMTP.
 */
@Component
class MailSendPolicy(
    private val mailHistoryRepository: MailHistoryRepository,
    private val mailProperties: MailProperties
) {

    fun enforceSendAllowed(userId: UUID) {
        val limit = mailProperties.limits.perUserPerHour
        val sentLastHour = mailHistoryRepository.countByUserIdAndCreatedAtAfter(
            userId,
            Instant.now().minusSeconds(3600)
        )
        if (sentLastHour >= limit) {
            throw IllegalStateException("Send limit reached ($limit per hour). Try again later.")
        }
    }

    fun validateAttachments(files: List<MultipartFile>) {
        val limits = mailProperties.limits
        if (files.size > limits.maxAttachments) {
            throw IllegalArgumentException("Too many attachments (max ${limits.maxAttachments})")
        }
        var total = 0L
        files.forEach { file ->
            if (file.size > limits.maxAttachmentBytes) {
                throw IllegalArgumentException(
                    "Attachment '${file.originalFilename}' exceeds ${limits.maxAttachmentBytes} bytes"
                )
            }
            total += file.size
        }
        if (total > limits.maxTotalAttachmentBytes) {
            throw IllegalArgumentException("Attachments exceed ${limits.maxTotalAttachmentBytes} bytes in total")
        }
    }
}
