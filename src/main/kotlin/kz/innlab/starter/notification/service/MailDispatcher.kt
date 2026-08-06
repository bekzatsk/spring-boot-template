package kz.innlab.starter.notification.service

import kz.innlab.starter.config.AsyncConfig
import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.notification.model.MailStatus
import kz.innlab.starter.notification.repository.MailHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import java.util.UUID

open class MailDispatcher(
    private val javaMailSender: JavaMailSender,
    private val mailHistoryRepository: MailHistoryRepository,
    private val mailProperties: MailProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MailDispatcher::class.java)
    }

    // Deliberately NOT @Transactional: SMTP attempts and retry sleeps must never pin a DB
    // connection to an open transaction. Each repository save below runs in its own short
    // transaction; a failing mail server then degrades mail delivery, not the DB pool.
    @Async(AsyncConfig.STARTER_EXECUTOR)
    open fun dispatchEmail(
        historyId: UUID,
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?,
        attachments: List<EmailAttachment>
    ) {
        // Runs on another thread long after the caller returned: the history row may be gone
        // (purged, or the owning request rolled back). Nothing to report status against, and an
        // exception here would only surface in the async handler's log.
        val history = mailHistoryRepository.findById(historyId).orElse(null)
        if (history == null) {
            logger.warn("Mail history {} disappeared before dispatch; skipping", historyId)
            return
        }

        for (attempt in 1..mailProperties.retry.maxAttempts) {
            try {
                val message = javaMailSender.createMimeMessage()
                val helper = MimeMessageHelper(message, attachments.isNotEmpty())
                helper.setFrom(mailProperties.smtp.from)
                helper.setTo(to)
                helper.setSubject(subject)

                if (htmlBody != null) {
                    helper.setText(htmlBody, true)
                } else {
                    helper.setText(textBody ?: "")
                }

                attachments.forEach { att ->
                    helper.addAttachment(att.filename, ByteArrayResource(att.bytes), att.contentType)
                }

                javaMailSender.send(message)
                history.status = MailStatus.SENT
                history.attempts = attempt
                saveQuietly(history)
                return
            } catch (e: Exception) {
                logger.warn("Email dispatch attempt {}/{} failed for {}: {}", attempt, mailProperties.retry.maxAttempts, historyId, e.message)
                history.attempts = attempt
                saveQuietly(history)
                if (attempt < mailProperties.retry.maxAttempts) {
                    try {
                        Thread.sleep(mailProperties.retry.delayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }

        logger.error("Email dispatch failed after {} attempts for {}", mailProperties.retry.maxAttempts, historyId)
        history.status = MailStatus.FAILED
        saveQuietly(history)
    }

    private fun saveQuietly(history: kz.innlab.starter.notification.model.MailHistory) {
        try {
            mailHistoryRepository.save(history)
        } catch (e: Exception) {
            logger.warn("Could not record mail status for {}: {}", history.id, e.message)
        }
    }

    open fun sendDirect(to: String, subject: String, textBody: String) {
        val message = SimpleMailMessage()
        message.from = mailProperties.smtp.from
        message.setTo(to)
        message.subject = subject
        message.text = textBody
        javaMailSender.send(message)
    }
}
