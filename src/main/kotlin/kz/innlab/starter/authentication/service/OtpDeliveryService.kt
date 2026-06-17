package kz.innlab.starter.authentication.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Optional

/**
 * High-level OTP delivery orchestrator.
 *
 * Strategy: try WhatsApp first (if configured). On any RuntimeException from the
 * WhatsApp provider, fall back to SMS. SMS failures bubble up to the caller so
 * upstream rate-limiting / 5xx behavior stays consistent with the previous
 * SMS-only flow.
 *
 * The WhatsApp dependency is optional (Spring injects Optional.empty() when no
 * bean is registered) — projects that don't configure Twilio WhatsApp keep the
 * legacy SMS-only behavior with no code change.
 */
@Service
class OtpDeliveryService(
    private val whatsAppService: Optional<WhatsAppService>,
    private val smsService: SmsService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OtpDeliveryService::class.java)
    }

    fun sendCode(phoneE164: String, code: String) {
        val whatsApp = whatsAppService.orElse(null)
        if (whatsApp != null) {
            try {
                whatsApp.sendCode(phoneE164, code)
                return
            } catch (ex: RuntimeException) {
                // Don't log code or full phone — keep PII out of logs.
                logger.warn(
                    "WhatsApp OTP delivery failed for {}, falling back to SMS: {}",
                    maskPhone(phoneE164),
                    ex.message
                )
            }
        }
        smsService.sendCode(phoneE164, code)
    }

    private fun maskPhone(phone: String): String =
        if (phone.length <= 4) "***" else phone.take(3) + "***" + phone.takeLast(2)
}
