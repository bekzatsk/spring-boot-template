package kz.innlab.template.config

import com.twilio.Twilio
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class TwilioConfig(
    @Value("\${app.auth.twilio.account-sid}")
    private val accountSid: String,

    @Value("\${app.auth.twilio.auth-token}")
    private val authToken: String
) {

    companion object {
        private val logger = LoggerFactory.getLogger(TwilioConfig::class.java)
    }

    /**
     * Initialize Twilio SDK once at startup (not per-request).
     * Twilio.init() does not make network calls — it only stores credentials.
     * Tests use placeholder values; actual Twilio calls are mocked via TwilioVerifyClient.
     */
    @PostConstruct
    fun init() {
        logger.info("Initializing Twilio SDK (account-sid={}...)", accountSid.take(8))
        Twilio.init(accountSid, authToken)
    }
}
