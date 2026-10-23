package kz.innlab.starter.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(
    val enabled: Boolean = false,
    val encryptionKey: String = "",
    val smtp: SmtpProperties = SmtpProperties(),
    val imap: ImapProperties = ImapProperties(),
    val retry: RetryProperties = RetryProperties(),
    val external: ExternalProperties = ExternalProperties(),
    val limits: SendLimits = SendLimits()
) {
    data class SmtpProperties(
        val host: String = "",
        val port: Int = 587,
        val username: String = "",
        val password: String = "",
        val from: String = "noreply@example.com",
        val sslEnabled: Boolean = false
    )

    data class ImapProperties(
        val host: String = "",
        val port: Int = 993,
        val username: String = "",
        val password: String = "",
        val sslEnabled: Boolean = true
    )

    data class RetryProperties(
        val maxAttempts: Int = 3,
        val delayMs: Long = 5000
    )

    data class ExternalProperties(
        val baseUrl: String = "",
        val masterKey: String = "",
        val connectTimeout: Duration = Duration.ofSeconds(5),
        /**
         * How long to wait for the service's answer. A mail service that delivers over SMTP
         * before answering takes several seconds; at the old fixed 15 s, mail that was sent got
         * reported as failed.
         */
        val readTimeout: Duration = Duration.ofSeconds(30)
    )

    /**
     * Abuse controls for the authenticated send endpoints. Without them any user can relay
     * unlimited mail with unbounded attachments through the application's sending domain.
     */
    data class SendLimits(
        val perUserPerHour: Int = 20,
        val maxAttachments: Int = 10,
        val maxAttachmentBytes: Long = 5L * 1024 * 1024,
        val maxTotalAttachmentBytes: Long = 20L * 1024 * 1024
    )
}
