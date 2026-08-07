package kz.innlab.starter.authentication.service

import org.slf4j.LoggerFactory

class ConsoleSmsService : SmsService {

    companion object {
        private val logger = LoggerFactory.getLogger(ConsoleSmsService::class.java)
    }

    override fun sendCode(phone: String, code: String) {
        // Never log the raw code: this fallback can end up active in a misconfigured deployment,
        // and logs are not a secret store. For local dev use app.auth.sms.dev-code instead.
        logger.info("[SMS] Sending verification code to {} (code hidden; set app.auth.sms.dev-code for local dev)", maskPhone(phone))
    }

    private fun maskPhone(phone: String): String =
        if (phone.length > 4) "*".repeat(phone.length - 4) + phone.takeLast(4) else "****"
}
