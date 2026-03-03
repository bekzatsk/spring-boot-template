package kz.innlab.template.config

import kz.innlab.template.authentication.service.EmailService
import kz.innlab.template.notification.repository.MailHistoryRepository
import kz.innlab.template.notification.service.ConsoleMailService
import kz.innlab.template.notification.service.MailDispatcher
import kz.innlab.template.notification.service.MailService
import kz.innlab.template.notification.service.SmtpMailService
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailConfig {

    @Bean
    @ConditionalOnBean(JavaMailSender::class)
    fun mailDispatcher(
        javaMailSender: JavaMailSender,
        mailHistoryRepository: MailHistoryRepository,
        mailProperties: MailProperties
    ): MailDispatcher = MailDispatcher(javaMailSender, mailHistoryRepository, mailProperties)

    @Bean
    @ConditionalOnBean(MailDispatcher::class)
    fun smtpMailService(
        mailProperties: MailProperties,
        mailHistoryRepository: MailHistoryRepository,
        mailDispatcher: MailDispatcher
    ): SmtpMailService = SmtpMailService(mailProperties, mailHistoryRepository, mailDispatcher)

    @Bean
    @ConditionalOnMissingBean(MailService::class)
    fun consoleMailService(): ConsoleMailService = ConsoleMailService()

    @Bean
    @ConditionalOnMissingBean(EmailService::class)
    fun emailService(mailService: MailService): EmailService = mailService as EmailService
}
