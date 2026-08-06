package kz.innlab.starter.autoconfigure

import kz.innlab.starter.config.DeviceTokenProperties
import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.notification.controller.MailController
import kz.innlab.starter.notification.controller.NotificationController
import kz.innlab.starter.notification.controller.TopicAdminController
import kz.innlab.starter.notification.repository.DeviceTokenRepository
import kz.innlab.starter.notification.repository.MailHistoryRepository
import kz.innlab.starter.notification.repository.NotificationHistoryRepository
import kz.innlab.starter.notification.repository.NotificationPreferenceRepository
import kz.innlab.starter.notification.repository.NotificationTopicRepository
import kz.innlab.starter.notification.service.DeviceTokenService
import kz.innlab.starter.notification.service.FirebasePushService
import kz.innlab.starter.notification.service.ImapService
import kz.innlab.starter.notification.service.MailHistoryService
import kz.innlab.starter.notification.service.MailSendPolicy
import kz.innlab.starter.notification.service.MailService
import kz.innlab.starter.notification.service.NotificationDispatcher
import kz.innlab.starter.notification.service.NotificationPreferenceService
import kz.innlab.starter.notification.service.NotificationService
import kz.innlab.starter.notification.service.PushService
import kz.innlab.starter.notification.service.TopicService
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Push notifications, topics, preferences and the mail-facing services. */
@Configuration(proxyBeanMethods = false)
class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun deviceTokenService(
        deviceTokenRepository: DeviceTokenRepository,
        deviceTokenProperties: DeviceTokenProperties
    ): DeviceTokenService = DeviceTokenService(deviceTokenRepository, deviceTokenProperties)

    @Bean
    @ConditionalOnMissingBean
    fun notificationPreferenceService(
        notificationPreferenceRepository: NotificationPreferenceRepository
    ): NotificationPreferenceService = NotificationPreferenceService(notificationPreferenceRepository)

    @Bean
    @ConditionalOnMissingBean
    fun notificationDispatcher(
        pushService: PushService,
        notificationHistoryRepository: NotificationHistoryRepository,
        deviceTokenService: DeviceTokenService
    ): NotificationDispatcher =
        NotificationDispatcher(pushService, notificationHistoryRepository, deviceTokenService)

    @Bean
    @ConditionalOnMissingBean
    fun notificationService(
        notificationHistoryRepository: NotificationHistoryRepository,
        notificationDispatcher: NotificationDispatcher,
        notificationPreferenceService: NotificationPreferenceService,
        deviceTokenRepository: DeviceTokenRepository,
        afterCommitRunner: AfterCommitRunner
    ): NotificationService = NotificationService(
        notificationHistoryRepository, notificationDispatcher,
        notificationPreferenceService, deviceTokenRepository, afterCommitRunner
    )

    @Bean
    @ConditionalOnMissingBean
    fun topicService(
        notificationTopicRepository: NotificationTopicRepository,
        pushService: PushService,
        deviceTokenRepository: DeviceTokenRepository
    ): TopicService = TopicService(notificationTopicRepository, pushService, deviceTokenRepository)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")
    fun firebasePushService(): FirebasePushService = FirebasePushService()

    // --- mail-facing services ---

    @Bean
    @ConditionalOnMissingBean
    fun imapService(mailProperties: MailProperties): ImapService = ImapService(mailProperties)

    @Bean
    @ConditionalOnMissingBean
    fun mailHistoryService(mailHistoryRepository: MailHistoryRepository): MailHistoryService =
        MailHistoryService(mailHistoryRepository)

    @Bean
    @ConditionalOnMissingBean
    fun mailSendPolicy(
        mailHistoryRepository: MailHistoryRepository,
        mailProperties: MailProperties
    ): MailSendPolicy = MailSendPolicy(mailHistoryRepository, mailProperties)

    // --- web layer ---

    @Bean
    @ConditionalOnMissingBean
    fun notificationController(
        deviceTokenService: DeviceTokenService,
        notificationService: NotificationService,
        topicService: TopicService,
        notificationPreferenceService: NotificationPreferenceService
    ): NotificationController = NotificationController(
        deviceTokenService, notificationService, topicService, notificationPreferenceService
    )

    @Bean
    @ConditionalOnMissingBean
    fun topicAdminController(topicService: TopicService): TopicAdminController =
        TopicAdminController(topicService)

    @Bean
    @ConditionalOnMissingBean
    fun mailController(
        mailService: MailService,
        imapService: ImapService,
        mailHistoryService: MailHistoryService,
        mailSendPolicy: MailSendPolicy
    ): MailController = MailController(mailService, imapService, mailHistoryService, mailSendPolicy)
}
