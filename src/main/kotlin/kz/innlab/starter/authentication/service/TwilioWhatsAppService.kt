package kz.innlab.starter.authentication.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import org.slf4j.LoggerFactory

/**
 * Twilio WhatsApp implementation.
 *
 * Business-initiated WhatsApp messages MUST use an approved Content Template.
 * Configure the template SID in Twilio Console (e.g. an "authentication" template
 * with one variable {{1}} for the OTP code).
 *
 * Throws ApiException (a RuntimeException) on Twilio API failure — the orchestrator
 * (OtpDeliveryService) catches that and falls back to SMS.
 *
 * Note: the Twilio static client must be initialized at app startup
 * (Twilio.init(accountSid, authToken)) — done in TwilioConfig.
 */
class TwilioWhatsAppService(
    private val fromWhatsApp: String,
    private val contentSid: String,
    private val objectMapper: ObjectMapper
) : WhatsAppService {

    companion object {
        private val logger = LoggerFactory.getLogger(TwilioWhatsAppService::class.java)
        private const val WHATSAPP_PREFIX = "whatsapp:"
        // Placeholder body — Twilio ignores it when contentSid is set, but the
        // SDK's 3-arg creator requires a non-null body string.
        private const val BODY_PLACEHOLDER = "OTP"
    }

    override fun sendCode(phone: String, code: String) {
        val from = PhoneNumber(ensureWhatsAppPrefix(fromWhatsApp))
        val to = PhoneNumber(ensureWhatsAppPrefix(phone))
        // Twilio expects content variables as a JSON string keyed by variable index.
        val variablesJson = objectMapper.writeValueAsString(mapOf("1" to code))
        // Body is required by the 3-arg creator but ignored by Twilio when contentSid is set.
        // We pass a non-empty placeholder so request validation passes.
        try {
            val msg = Message.creator(to, from, BODY_PLACEHOLDER)
                .setContentSid(contentSid)
                .setContentVariables(variablesJson)
                .create()
            logger.debug("WhatsApp OTP queued via Twilio: sid={}", msg.sid)
        } catch (ex: ApiException) {
            // Re-throw to let OtpDeliveryService fall back to SMS.
            throw ex
        }
    }

    private fun ensureWhatsAppPrefix(phone: String): String =
        if (phone.startsWith(WHATSAPP_PREFIX)) phone else WHATSAPP_PREFIX + phone
}
