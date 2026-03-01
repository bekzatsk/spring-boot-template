package kz.innlab.template.authentication.service

import com.twilio.rest.verify.v2.service.Verification
import com.twilio.rest.verify.v2.service.VerificationCheck
import org.springframework.stereotype.Component

/**
 * Abstraction over Twilio Verify API calls.
 * Enables clean mocking in integration tests without hitting Twilio network.
 */
interface TwilioVerifyClient {
    /**
     * Send a verification code to the given phone number via the specified channel (e.g. "sms").
     */
    fun sendVerification(serviceSid: String, to: String, channel: String)

    /**
     * Check a verification code. Returns the status string (e.g. "approved", "pending").
     */
    fun checkVerification(serviceSid: String, to: String, code: String): String
}

@Component
class DefaultTwilioVerifyClient : TwilioVerifyClient {

    override fun sendVerification(serviceSid: String, to: String, channel: String) {
        Verification.creator(serviceSid, to, channel).create()
    }

    override fun checkVerification(serviceSid: String, to: String, code: String): String {
        val check = VerificationCheck.creator(serviceSid).setTo(to).setCode(code).create()
        return check.status
    }
}
