package kz.innlab.consumer

import kz.innlab.starter.authentication.service.OtpDeliveryChannel
import kz.innlab.starter.authentication.service.OtpDeliveryException
import kz.innlab.starter.authentication.service.OtpDeliveryService
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.authentication.service.WhatsAppService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * `app.auth.otp.delivery-order` in a consumer context: the channels are the beans the application
 * actually has (SmsService, WhatsAppService, any OtpDeliveryChannel such as the Telegram Gateway),
 * tried in the configured order.
 */
class OtpDeliveryOrderIntegrationTest {

    private val phone = "+77011234567"
    private val code = "482915"

    /** Every attempt, in order, as "name" or "name!" for a failed one. */
    private val attempts: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private fun recording(name: String, fails: Boolean = false): (String, String) -> Unit = { p, c ->
        assertThat(p).isEqualTo(phone)
        assertThat(c).isEqualTo(code)
        attempts += if (fails) "$name!" else name
        if (fails) throw IllegalStateException("$name down")
    }

    private fun sms(fails: Boolean = false) = recording("sms", fails).let { send ->
        object : SmsService { override fun sendCode(phone: String, code: String) = send(phone, code) }
    }

    private fun whatsApp(fails: Boolean = false) = recording("whatsapp", fails).let { send ->
        object : WhatsAppService { override fun sendCode(phone: String, code: String) = send(phone, code) }
    }

    private fun telegram(fails: Boolean = false) = recording("telegram", fails).let { send ->
        object : OtpDeliveryChannel {
            override val name = "telegram"
            override fun sendCode(phone: String, code: String) = send(phone, code)
        }
    }

    @Test
    fun `default order is whatsapp then sms, as before the setting existed`() {
        consumerContextRunner()
            .withBean(SmsService::class.java, { sms() })
            .withBean(WhatsAppService::class.java, { whatsApp(fails = true) })
            .withBean(OtpDeliveryChannel::class.java, { telegram() })
            .run { context ->
                context.getBean(OtpDeliveryService::class.java).sendCode(phone, code)
                // telegram is available but not in the default order, so it is never tried
                assertThat(attempts).containsExactly("whatsapp!", "sms")
            }
    }

    @Test
    fun `channels are tried in the configured order and the first success ends delivery`() {
        consumerContextRunner("app.auth.otp.delivery-order=telegram,whatsapp,sms")
            .withBean(SmsService::class.java, { sms() })
            .withBean(WhatsAppService::class.java, { whatsApp() })
            .withBean(OtpDeliveryChannel::class.java, { telegram() })
            .run { context ->
                context.getBean(OtpDeliveryService::class.java).sendCode(phone, code)
                assertThat(attempts).containsExactly("telegram")
            }
    }

    @Test
    fun `a failing channel hands over to the next one`() {
        consumerContextRunner("app.auth.otp.delivery-order=telegram,whatsapp,sms")
            .withBean(SmsService::class.java, { sms() })
            .withBean(WhatsAppService::class.java, { whatsApp(fails = true) })
            .withBean(OtpDeliveryChannel::class.java, { telegram(fails = true) })
            .run { context ->
                context.getBean(OtpDeliveryService::class.java).sendCode(phone, code)
                assertThat(attempts).containsExactly("telegram!", "whatsapp!", "sms")
            }
    }

    @Test
    fun `the last channel's failure reaches the caller`() {
        consumerContextRunner("app.auth.otp.delivery-order=sms,telegram")
            .withBean(SmsService::class.java, { sms(fails = true) })
            .withBean(OtpDeliveryChannel::class.java, { telegram(fails = true) })
            .run { context ->
                val service = context.getBean(OtpDeliveryService::class.java)
                assertThatThrownBy { service.sendCode(phone, code) }
                    .isInstanceOf(OtpDeliveryException::class.java)
                    .hasMessageContaining("sms (java.lang.IllegalStateException)")
                    .hasMessageContaining("telegram (java.lang.IllegalStateException)")
                    .hasMessageNotContaining(code)
                    .hasMessageNotContaining(phone.removePrefix("+"))
                assertThat(attempts).containsExactly("sms!", "telegram!")
            }
    }

    @Test
    fun `a listed channel without a bean is skipped`() {
        // Neither the Telegram Gateway nor WhatsApp is configured in this application.
        consumerContextRunner("app.auth.otp.delivery-order=telegram,whatsapp,sms")
            .withBean(SmsService::class.java, { sms() })
            .run { context ->
                assertThat(context).hasNotFailed()
                context.getBean(OtpDeliveryService::class.java).sendCode(phone, code)
                assertThat(attempts).containsExactly("sms")
            }
    }

    @Test
    fun `an order with no available channel fails startup`() {
        consumerContextRunner("app.auth.otp.delivery-order=telegram,whatsapp")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("app.auth.otp.delivery-order")
            }
    }

    @Test
    fun `an empty order fails startup`() {
        consumerContextRunner("app.auth.otp.delivery-order=")
            .run { context -> assertThat(context).hasFailed() }
    }

    @Test
    fun `the Telegram Gateway, once enabled, is a channel named telegram`() {
        consumerContextRunner(
            "app.auth.telegram-gateway.enabled=true",
            "app.auth.telegram-gateway.token=gw-test-token",
            "app.auth.otp.delivery-order=telegram"
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(OtpDeliveryChannel::class.java).name).isEqualTo("telegram")
        }
    }
}
