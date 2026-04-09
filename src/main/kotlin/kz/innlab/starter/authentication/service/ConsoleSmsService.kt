package kz.innlab.starter.authentication.service

import org.slf4j.LoggerFactory

class ConsoleSmsService : SmsService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsoleSmsService::class.java)
    }

    override fun sendCode(phone: String, code: String) {
        logger.info("[SMS] Sending code {} to {}", code, phone)
    }
}
