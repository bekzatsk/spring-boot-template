package kz.innlab.starter.config

import kz.innlab.starter.authentication.repository.SmsVerificationRepository
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.ConsoleSmsService
import kz.innlab.starter.authentication.service.SmsService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Configuration
@EnableScheduling
class SmsSchedulerConfig {

    companion object {
        private val logger = LoggerFactory.getLogger(SmsSchedulerConfig::class.java)
    }

    @Bean
    @ConditionalOnMissingBean(SmsService::class)
    fun smsService(): SmsService = ConsoleSmsService()

    @Configuration
    @ConditionalOnProperty(name = ["app.auth.phone.enabled"], havingValue = "true", matchIfMissing = true)
    class SmsCleanupScheduler(
        private val smsVerificationRepository: SmsVerificationRepository
    ) {
        @Scheduled(fixedRate = 600_000)
        @Transactional
        fun cleanupExpiredSmsCodes() {
            logger.debug("Running SMS verification cleanup job")
            smsVerificationRepository.deleteExpiredOrUsed(Instant.now())
        }
    }

    @Configuration
    @ConditionalOnProperty(name = ["app.auth.local.enabled"], havingValue = "true", matchIfMissing = true)
    class VerificationCodeCleanupScheduler(
        private val verificationCodeRepository: VerificationCodeRepository
    ) {
        @Scheduled(fixedRate = 600_000)
        @Transactional
        fun cleanupExpiredVerificationCodes() {
            logger.debug("Running verification code cleanup job")
            verificationCodeRepository.deleteExpiredOrUsed(Instant.now())
        }
    }
}
