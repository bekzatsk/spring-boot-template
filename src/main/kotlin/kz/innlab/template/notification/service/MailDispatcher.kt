package kz.innlab.template.notification.service

import kz.innlab.template.config.MailProperties
import kz.innlab.template.notification.model.MailStatus
import kz.innlab.template.notification.repository.MailHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class MailDispatcher(
    private val javaMailSender: JavaMailSender,
    private val mailHistoryRepository: MailHistoryRepository,
    private val mailProperties: MailProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MailDispatcher::class.java)
    }

    @Async
    @Transactional
    fun dispatchEmail(
        historyId: UUID,
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?,
        attachments: List<EmailAttachment>
    ) {
        val history = mailHistoryRepository.findById(historyId).orElseThrow()

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
                mailHistoryRepository.save(history)
                return
            } catch (e: Exception) {
                logger.warn("Email dispatch attempt {}/{} failed for {}: {}", attempt, mailProperties.retry.maxAttempts, historyId, e.message)
                history.attempts = attempt
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
        mailHistoryRepository.save(history)
    }

    fun sendDirect(to: String, subject: String, textBody: String) {
        val message = SimpleMailMessage()
        message.from = mailProperties.smtp.from
        message.setTo(to)
        message.subject = subject
        message.text = textBody
        javaMailSender.send(message)
    }
}
