package kz.innlab.starter.notification.service

import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.notification.model.MailHistory
import kz.innlab.starter.notification.model.MailStatus
import kz.innlab.starter.notification.repository.MailHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.util.UUID

class ExternalMailService(
    private val mailProperties: MailProperties,
    private val mailHistoryRepository: MailHistoryRepository
) : MailService, EmailService {

    private val logger = LoggerFactory.getLogger(ExternalMailService::class.java)

    private val restClient = RestClient.builder()
        .baseUrl(mailProperties.external.baseUrl)
        .defaultHeader("X-Api-Key", mailProperties.external.masterKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    override fun send(
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?,
        attachments: List<EmailAttachment>
    ) {
        val body = buildRequestBody(to, subject, textBody, htmlBody)
        try {
            restClient.post()
                .uri("/send")
                .body(body)
                .retrieve()
                .toBodilessEntity()
            logger.debug("Email sent via external service to {}", to)
        } catch (e: Exception) {
            logger.error("External mail service error for {}: {}", to, e.message)
            throw e
        }
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

        try {
            send(to, subject, textBody, htmlBody, attachments)
            history.status = MailStatus.SENT
            history.attempts = 1
        } catch (e: Exception) {
            history.status = MailStatus.FAILED
            history.attempts = 1
        }
        mailHistoryRepository.save(history)
        return history.id
    }

    override fun sendCode(to: String, code: String, purpose: String) {
        send(to, "$purpose Verification Code", "Your $purpose code is: $code", null)
    }

    private fun buildRequestBody(
        to: String,
        subject: String,
        textBody: String?,
        htmlBody: String?
    ): Map<String, Any?> = buildMap {
        put("to", to)
        put("subject", subject)
        put("body", htmlBody ?: textBody ?: "")
        if (htmlBody != null) put("isHtml", true)
    }
}
