package kz.innlab.starter.notification.service

import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.notification.model.MailHistory
import kz.innlab.starter.notification.repository.MailHistoryRepository
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import org.slf4j.LoggerFactory
import java.util.UUID

class SmtpMailService(
    private val mailProperties: MailProperties,
    private val mailHistoryRepository: MailHistoryRepository,
    private val mailDispatcher: MailDispatcher,
    private val afterCommitRunner: AfterCommitRunner
) : MailService, EmailService {

    companion object {
        private val logger = LoggerFactory.getLogger(SmtpMailService::class.java)
    }

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

        // After commit: dispatchEmail re-reads the history row asynchronously and must not race
        // an uncommitted insert when a caller wraps sendEmail in a transaction.
        afterCommitRunner.run { mailDispatcher.dispatchEmail(history.id, to, subject, textBody, htmlBody, attachments) }
        return history.id
    }

    override fun sendCode(to: String, code: String, purpose: String) {
        // Callers (registration, password reset) invoke this inside their own transaction; the
        // SMTP round-trip is deferred to after commit so it never blocks the transaction. Failures
        // are logged, not propagated — the flow has already committed and the user can re-request.
        afterCommitRunner.run {
            try {
                mailDispatcher.sendDirect(to, "$purpose Verification Code", "Your $purpose code is: $code")
            } catch (e: Exception) {
                logger.error("Failed to send {} code email to {}: {}", purpose, to, e.message, e)
            }
        }
    }
}
