package kz.innlab.starter.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.twilio.Twilio
import jakarta.annotation.PostConstruct
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.authentication.service.TwilioSmsService
import kz.innlab.starter.authentication.service.TwilioWhatsAppService
import kz.innlab.starter.authentication.service.WhatsAppService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Twilio OTP delivery configuration.
 *
 * Activated when `app.twilio.enabled=true`. Initializes the Twilio static client
 * once at startup with account SID + auth token from config.
 *
 * Beans exposed (conditionally):
 *  - TwilioWhatsAppService — when `app.twilio.whatsapp.enabled=true` AND `content-sid` set.
 *    Tried first by OtpDeliveryService.
 *  - TwilioSmsService — when `app.twilio.sms.enabled=true`. Replaces the default
 *    ConsoleSmsService (which has @ConditionalOnMissingBean(SmsService::class) and
 *    therefore skips registration when this Twilio bean is present).
 *
 * Either channel can be enabled independently. If only WhatsApp is configured and
 * delivery fails, the console SMS fallback is used (dev). In prod, enable both.
 */
@Configuration
@ConditionalOnProperty(name = ["app.twilio.enabled"], havingValue = "true")
@ConfigurationProperties(prefix = "app.twilio")
class TwilioConfig {

    /** Master toggle. Mirrors the @ConditionalOnProperty marker so the field
     *  binds cleanly and IDE configuration metadata recognises it. */
    var enabled: Boolean = false
    var accountSid: String = ""
    var authToken: String = ""
    val whatsapp: WhatsAppProps = WhatsAppProps()
    val sms: SmsProps = SmsProps()

    class WhatsAppProps {
        var enabled: Boolean = false
        /** E.164 number registered in Twilio (without the `whatsapp:` prefix; added automatically). */
        var from: String = ""
        /** Twilio Content Template SID (HX...). Required for business-initiated WhatsApp OTP. */
        var contentSid: String = ""
    }

    class SmsProps {
        var enabled: Boolean = false
        /** E.164 sender number or alphanumeric sender ID (where supported). */
        var from: String = ""
    }

    @PostConstruct
    fun initTwilio() {
        require(accountSid.isNotBlank()) { "app.twilio.account-sid must be set when app.twilio.enabled=true" }
        require(authToken.isNotBlank()) { "app.twilio.auth-token must be set when app.twilio.enabled=true" }
        Twilio.init(accountSid, authToken)
    }

    @Bean
    @ConditionalOnProperty(name = ["app.twilio.whatsapp.enabled"], havingValue = "true")
    fun twilioWhatsAppService(objectMapper: ObjectMapper): WhatsAppService {
        require(whatsapp.from.isNotBlank()) { "app.twilio.whatsapp.from is required" }
        require(whatsapp.contentSid.isNotBlank()) { "app.twilio.whatsapp.content-sid is required" }
        return TwilioWhatsAppService(whatsapp.from, whatsapp.contentSid, objectMapper)
    }

    @Bean
    @ConditionalOnProperty(name = ["app.twilio.sms.enabled"], havingValue = "true")
    fun twilioSmsService(): SmsService {
        require(sms.from.isNotBlank()) { "app.twilio.sms.from is required" }
        return TwilioSmsService(sms.from)
    }
}
