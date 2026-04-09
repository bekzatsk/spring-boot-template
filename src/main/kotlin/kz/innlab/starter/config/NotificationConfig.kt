package kz.innlab.starter.config

import kz.innlab.starter.notification.service.ConsolePushService
import kz.innlab.starter.notification.service.PushService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NotificationConfig {

    @Bean
    @ConditionalOnMissingBean(PushService::class)
    fun pushService(): PushService = ConsolePushService()
}
