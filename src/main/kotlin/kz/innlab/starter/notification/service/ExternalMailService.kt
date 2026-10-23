package kz.innlab.starter.notification.service

import org.springframework.http.client.SimpleClientHttpRequestFactory
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.shared.util.maskEmail
import kz.innlab.starter.authentication.service.DefaultVerificationEmailMessages
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.VerificationEmailMessages
import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.notification.model.MailHistory
import kz.innlab.starter.notification.model.MailStatus
import kz.innlab.starter.notification.repository.MailHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import java.util.UUID

class ExternalMailService(
    private val mailProperties: MailProperties,
    private val mailHistoryRepository: MailHistoryRepository,
    private val afterCommitRunner: AfterCommitRunner = AfterCommitRunner(),
    private val verificationEmailMessages: VerificationEmailMessages = DefaultVerificationEmailMessages()
) : MailService, EmailService {

    private val logger = LoggerFactory.getLogger(ExternalMailService::class.java)

    // Bounded: without timeouts a stalled provider held the request thread (and, for codes, the
    // caller's database transaction) indefinitely.
    private val restClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(mailProperties.external.connectTimeout)
            setReadTimeout(mailProperties.external.readTimeout)
        })
        .baseUrl(mailProperties.external.baseUrl)
        .defaultHeader("X-Api-Key", mailProperties.external.masterKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /**
     * Throws [MailOutcomeUnknownException] when the request reached the service but no answer
     * came back in time: the service may well have sent the email, so this is not a failure.
     */
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
            logger.info("Email sent via external service to {}", maskEmail(to))
        } catch (e: Exception) {
            if (isReadTimeout(e)) {
                logger.warn(
                    "External mail service did not answer within {} for {}; the email may have been sent",
                    mailProperties.external.readTimeout, maskEmail(to)
                )
                throw MailOutcomeUnknownException(e)
            }
            logger.error("External mail service error for {}: {}", maskEmail(to), e.message)
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
        } catch (e: MailOutcomeUnknownException) {
            // Neither SENT nor FAILED is known; the row stays PENDING.
        } catch (e: Exception) {
            history.status = MailStatus.FAILED
        }
        history.attempts = 1
        mailHistoryRepository.save(history)
        return history.id
    }

    override fun sendCode(to: String, code: String, purpose: String) {
        // Callers issue codes inside their transaction. The HTTP call waits for the commit — it
        // must not hold a connection open, and a provider failure must not roll back the
        // registration or re-authentication that produced the code. Failures are logged by send;
        // the user can request a new code.
        afterCommitRunner.run {
            val email = verificationEmailMessages.compose(purpose, code)
            try {
                send(to, email.subject, email.text, email.html)
            } catch (e: Exception) {
                // Already logged with the reason; this line only names the flow.
                logger.debug("{} code email to {} not confirmed", purpose, maskEmail(to))
            }
        }
    }

    private fun isReadTimeout(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(10).any {
            it is SocketTimeoutException && it.message?.contains("connect", ignoreCase = true) != true
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

/** The mail service was reached but did not answer in time; the email may have been sent. */
class MailOutcomeUnknownException(cause: Throwable) :
    RuntimeException("Mail service did not answer in time; delivery is unknown", cause)
