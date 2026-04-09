package kz.innlab.starter.notification.service

import kz.innlab.starter.authentication.service.EmailService
import org.slf4j.LoggerFactory

class ConsoleEmailService : EmailService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsoleEmailService::class.java)
    }

    override fun sendCode(to: String, code: String, purpose: String) {
        logger.info("[EMAIL] Sending {} code {} to {}", purpose, code, to)
    }
}
