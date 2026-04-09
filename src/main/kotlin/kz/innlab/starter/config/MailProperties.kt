package kz.innlab.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(
    val enabled: Boolean = false,
    val encryptionKey: String = "",
    val smtp: SmtpProperties = SmtpProperties(),
    val imap: ImapProperties = ImapProperties(),
    val retry: RetryProperties = RetryProperties(),
    val external: ExternalProperties = ExternalProperties()
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
        val masterKey: String = ""
    )
}
