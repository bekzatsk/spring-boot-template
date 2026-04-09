package kz.innlab.starter.config

import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.notification.repository.MailHistoryRepository
import kz.innlab.starter.notification.service.ConsoleEmailService
import kz.innlab.starter.notification.service.ConsoleMailService
import kz.innlab.starter.notification.service.MailDispatcher
import kz.innlab.starter.notification.service.MailService
import kz.innlab.starter.notification.service.SmtpMailService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailConfig {

    @Bean
    @ConditionalOnProperty(name = ["app.mail.enabled"], havingValue = "true")
    fun mailDispatcher(
        javaMailSender: JavaMailSender,
        mailHistoryRepository: MailHistoryRepository,
        mailProperties: MailProperties
    ): MailDispatcher = MailDispatcher(javaMailSender, mailHistoryRepository, mailProperties)

    @Bean
    @ConditionalOnProperty(name = ["app.mail.enabled"], havingValue = "true")
    fun smtpMailService(
        mailProperties: MailProperties,
        mailHistoryRepository: MailHistoryRepository,
        mailDispatcher: MailDispatcher
    ): SmtpMailService = SmtpMailService(mailProperties, mailHistoryRepository, mailDispatcher)

    @Bean
    @ConditionalOnMissingBean(MailService::class)
    fun consoleMailService(): MailService = ConsoleMailService()

    @Bean
    @ConditionalOnMissingBean(EmailService::class)
    fun consoleEmailService(): EmailService = ConsoleEmailService()
}
