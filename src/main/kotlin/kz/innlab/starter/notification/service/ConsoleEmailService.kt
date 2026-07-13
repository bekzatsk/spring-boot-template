package kz.innlab.starter.notification.service

import kz.innlab.starter.authentication.service.EmailService
import org.slf4j.LoggerFactory

/**
 * Console fallback for verification-code emails ([EmailService]), separate from [ConsoleMailService].
 *
 * Kept as a distinct single-interface bean so mocking [EmailService] in tests (@MockitoBean) replaces
 * only this bean and does not shadow the [MailService] console bean — which previously broke
 * MailController when EmailService and MailService were served by one dual-interface bean.
 */
class ConsoleEmailService : EmailService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsoleEmailService::class.java)
    }

    override fun sendCode(to: String, code: String, purpose: String) {
        logger.info("[EMAIL] Sending {} code {} to {}", purpose, code, to)
    }
}
