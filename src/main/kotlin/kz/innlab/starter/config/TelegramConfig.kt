package kz.innlab.starter.config

import kz.innlab.starter.authentication.repository.TelegramAuthSessionRepository
import kz.innlab.starter.authentication.service.ConsoleTelegramBotService
import kz.innlab.starter.authentication.service.RealTelegramBotService
import kz.innlab.starter.authentication.service.TelegramBotService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Configuration
@ConditionalOnProperty(name = ["app.auth.telegram.enabled"], havingValue = "true")
class TelegramConfig {

    /**
     * Uses RealTelegramBotService when `app.auth.telegram.bot-token` is configured.
     * Falls back to ConsoleTelegramBotService (logs only) when token is blank — useful for dev.
     * Consumers can fully override by defining their own `TelegramBotService` bean.
     */
    @Bean
    @ConditionalOnMissingBean(TelegramBotService::class)
    fun telegramBotService(
        @Value("\${app.auth.telegram.bot-token:}") botToken: String
    ): TelegramBotService =
        if (botToken.isNotBlank()) RealTelegramBotService(botToken)
        else ConsoleTelegramBotService()

    @Configuration
    @ConditionalOnProperty(name = ["app.auth.telegram.enabled"], havingValue = "true")
    class TelegramSessionCleanupScheduler(
        private val telegramAuthSessionRepository: TelegramAuthSessionRepository
    ) {

        companion object {
            private val logger = LoggerFactory.getLogger(TelegramSessionCleanupScheduler::class.java)
        }

        @Scheduled(fixedRate = 600_000)
        @Transactional
        fun cleanupExpiredSessions() {
            logger.debug("Running Telegram auth session cleanup job")
            telegramAuthSessionRepository.deleteExpired(Instant.now())
        }
    }
}
