package kz.innlab.template.notification.service

import kz.innlab.template.authentication.service.EmailService
import kz.innlab.template.config.MailProperties
import kz.innlab.template.notification.model.MailHistory
import kz.innlab.template.notification.repository.MailHistoryRepository
import java.util.UUID

class SmtpMailService(
    private val mailProperties: MailProperties,
    private val mailHistoryRepository: MailHistoryRepository,
    private val mailDispatcher: MailDispatcher
) : MailService, EmailService {

    override fun send(
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?,
        attachments: List<EmailAttachment>
    ) {
        mailDispatcher.sendDirect(to, subject, textBody ?: "")
    }

    override fun sendEmail(
        userId: UUID,
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?,
        attachments: List<EmailAttachment>
    ): UUID {
        val history = MailHistory(userId, to, subject)
        history.textBody = textBody
        history.htmlBody = htmlBody
        history.hasAttachments = attachments.isNotEmpty()
        mailHistoryRepository.save(history)

        mailDispatcher.dispatchEmail(history.id, to, subject, textBody, htmlBody, attachments)
        return history.id
    }

    override fun sendCode(to: String, code: String, purpose: String) {
        mailDispatcher.sendDirect(to, "$purpose Verification Code", "Your $purpose code is: $code")
    }
}
