package kz.innlab.template.config

import kz.innlab.template.authentication.repository.SmsVerificationRepository
import kz.innlab.template.authentication.repository.VerificationCodeRepository
import kz.innlab.template.authentication.service.ConsoleSmsService
import kz.innlab.template.authentication.service.SmsService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Configuration
@EnableScheduling
class SmsSchedulerConfig(
    private val smsVerificationRepository: SmsVerificationRepository,
    private val verificationCodeRepository: VerificationCodeRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(SmsSchedulerConfig::class.java)
    }

    @Scheduled(fixedRate = 600_000)
    @Transactional
    fun cleanupExpiredCodes() {
        logger.debug("Running verification cleanup job")
        smsVerificationRepository.deleteExpiredOrUsed(Instant.now())
        verificationCodeRepository.deleteExpiredOrUsed(Instant.now())
    }

    @Bean
    @ConditionalOnMissingBean(SmsService::class)
    fun smsService(): SmsService = ConsoleSmsService()
}
