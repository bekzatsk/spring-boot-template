package kz.innlab.starter.config

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

    /**
     * One job for every channel: phone OTPs live in verification_codes alongside the
     * email/Telegram codes, so the separate SMS cleanup job is gone.
     */
    @Configuration
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
