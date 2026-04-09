package kz.innlab.starter.notification.service

import com.github.f4b6a3.uuid.UuidCreator
import kz.innlab.starter.authentication.service.EmailService
import org.slf4j.LoggerFactory
import java.util.UUID

class ConsoleMailService : MailService, EmailService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsoleMailService::class.java)
    }

    override fun send(
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?,
        attachments: List<EmailAttachment>
    ) {
        logger.info("[MAIL] Sending email to {}, subject: {}, hasAttachments: {}", to, subject, attachments.isNotEmpty())
    }

    override fun sendEmail(
        userId: UUID,
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?,
        attachments: List<EmailAttachment>
    ): UUID {
        logger.info("[MAIL] Sending tracked email to {}, subject: {}, hasAttachments: {}", to, subject, attachments.isNotEmpty())
        return UuidCreator.getTimeOrderedEpoch()
    }

    override fun sendCode(to: String, code: String, purpose: String) {
        logger.info("[EMAIL] Sending {} code {} to {}", purpose, code, to)
    }
}
