package kz.innlab.starter.authentication.service

import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import org.slf4j.LoggerFactory

/**
 * Twilio Programmable SMS implementation.
 *
 * Used by OtpDeliveryService as the fallback channel when WhatsApp delivery fails
 * or when no WhatsApp bean is configured. Exceptions bubble up to the controller
 * (Twilio's ApiException is a RuntimeException).
 *
 * Twilio static client init happens in TwilioConfig.
 */
class TwilioSmsService(
    private val fromSms: String
) : SmsService {

    companion object {
        private val logger = LoggerFactory.getLogger(TwilioSmsService::class.java)
    }

    override fun sendCode(phone: String, code: String) {
        val msg = Message.creator(
            PhoneNumber(phone),
            PhoneNumber(fromSms),
            "Your verification code: $code"
        ).create()
        logger.debug("SMS OTP queued via Twilio: sid={}", msg.sid)
    }
}
