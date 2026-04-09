package kz.innlab.starter.config

import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.notification.repository.MailHistoryRepository
import kz.innlab.starter.notification.service.ConsoleEmailService
import kz.innlab.starter.notification.service.ConsoleMailService
import kz.innlab.starter.notification.service.ExternalMailService
import kz.innlab.starter.notification.service.MailDispatcher
import kz.innlab.starter.notification.service.MailService
import kz.innlab.starter.notification.service.SmtpMailService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl

@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailConfig {

    // --- External mail service (HTTP API) ---

    @Bean
    @ConditionalOnProperty(name = ["app.mail.external.base-url"], matchIfMissing = false)
    fun externalMailService(
        mailProperties: MailProperties,
        mailHistoryRepository: MailHistoryRepository
    ): ExternalMailService = ExternalMailService(mailProperties, mailHistoryRepository)

    // --- Direct SMTP (only when external is not configured) ---

    @Bean
    @ConditionalOnMissingBean(JavaMailSender::class)
    @ConditionalOnProperty(name = ["app.mail.enabled"], havingValue = "true")
    fun javaMailSender(mailProperties: MailProperties): JavaMailSender {
        val sender = JavaMailSenderImpl()
        sender.host = mailProperties.smtp.host
        sender.port = mailProperties.smtp.port
        sender.username = mailProperties.smtp.username
        sender.password = mailProperties.smtp.password
        val hasCredentials = mailProperties.smtp.username.isNotBlank()
        sender.javaMailProperties["mail.smtp.auth"] = hasCredentials.toString()
        sender.javaMailProperties["mail.smtp.starttls.enable"] = mailProperties.smtp.sslEnabled.toString()
        return sender
    }

    @Bean
    @ConditionalOnMissingBean(MailService::class)
    @ConditionalOnProperty(name = ["app.mail.enabled"], havingValue = "true")
    fun mailDispatcher(
        javaMailSender: JavaMailSender,
        mailHistoryRepository: MailHistoryRepository,
        mailProperties: MailProperties
    ): MailDispatcher = MailDispatcher(javaMailSender, mailHistoryRepository, mailProperties)

    @Bean
    @ConditionalOnMissingBean(MailService::class)
    @ConditionalOnProperty(name = ["app.mail.enabled"], havingValue = "true")
    fun smtpMailService(
        mailProperties: MailProperties,
        mailHistoryRepository: MailHistoryRepository,
        mailDispatcher: MailDispatcher
    ): SmtpMailService = SmtpMailService(mailProperties, mailHistoryRepository, mailDispatcher)

    // --- Fallback (console logging) ---

    @Bean
    @ConditionalOnMissingBean(MailService::class)
    fun consoleMailService(): MailService = ConsoleMailService()

    @Bean
    @ConditionalOnMissingBean(EmailService::class)
    fun consoleEmailService(): EmailService = ConsoleEmailService()
}
