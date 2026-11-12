package kz.innlab.starter.authentication.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kz.innlab.starter.config.OtpDeliveryProperties
import java.util.Optional
import kotlin.test.assertEquals

class OtpDeliveryServiceTest {

    private val phone = "+15551234567"
    private val code = "123456"

    /** Default order — whatsapp, then sms — exactly the behaviour before the order was configurable. */
    private fun service(
        whatsApp: WhatsAppService?,
        sms: SmsService,
        channels: List<OtpDeliveryChannel> = emptyList(),
        order: List<String> = OtpDeliveryProperties().deliveryOrder
    ) = OtpDeliveryService(Optional.ofNullable(whatsApp), sms, channels, OtpDeliveryProperties(order))

    private fun channel(name: String): OtpDeliveryChannel {
        val channel = mock(OtpDeliveryChannel::class.java)
        org.mockito.Mockito.`when`(channel.name).thenReturn(name)
        return channel
    }

    @Test
    fun `a failing channel's message does not put the code or the full phone into the log`() {
        val logger = org.slf4j.LoggerFactory.getLogger(OtpDeliveryService::class.java) as ch.qos.logback.classic.Logger
        val appender = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val whatsApp = mock(WhatsAppService::class.java)
            val sms = mock(SmsService::class.java)
            // Provider exceptions echo the request: Twilio's ApiException names the "To" number.
            doThrow(RuntimeException("Unable to deliver $code to $phone (${phone.removePrefix("+")})"))
                .`when`(whatsApp).sendCode(phone, code)

            service(whatsApp, sms).sendCode(phone, code)

            verify(sms).sendCode(phone, code)
            val logged = appender.list.map { it.formattedMessage }
            assertEquals(true, logged.isNotEmpty(), "the fallback is still logged")
            logged.forEach { line ->
                assertEquals(false, line.contains(code), "code in log: $line")
                assertEquals(false, line.contains(phone.removePrefix("+")), "full phone in log: $line")
            }
            assertEquals(true, appender.list.all { it.throwableProxy == null }, "no stack trace carrying the message")
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Test
    fun `the last channel's failure reaches the caller without the code or the full phone`() {
        val sms = mock(SmsService::class.java)
        doThrow(IllegalStateException("Unable to deliver $code to $phone")).`when`(sms).sendCode(phone, code)

        val ex = assertThrows<OtpDeliveryException> { service(null, sms).sendCode(phone, code) }

        // Handlers log the whole chain with its stack trace: nothing in it may carry the provider's text.
        val chain = generateSequence<Throwable>(ex) { it.cause }.toList() + ex.suppressed
        chain.forEach { t ->
            assertEquals(false, t.toString().contains(code), "code in: $t")
            assertEquals(false, t.toString().contains(phone.removePrefix("+")), "full phone in: $t")
        }
        assertEquals(true, ex.message!!.contains("+15***67"), ex.message)
    }

    @Test
    fun `default order is whatsapp then sms`() {
        assertEquals(listOf("whatsapp", "sms"), OtpDeliveryProperties().deliveryOrder)
    }

    @Test
    fun `follows the configured order`() {
        val whatsApp = mock(WhatsAppService::class.java)
        val sms = mock(SmsService::class.java)
        val service = service(whatsApp, sms, order = listOf("sms", "whatsapp"))

        service.sendCode(phone, code)

        verify(sms).sendCode(phone, code)
        verifyNoInteractions(whatsApp)
    }

    @Test
    fun `names are matched without case or surrounding blanks`() {
        val telegram = channel("telegram")
        val sms = mock(SmsService::class.java)
        val service = service(null, sms, listOf(telegram), listOf(" Telegram ", "SMS"))

        service.sendCode(phone, code)

        verify(telegram).sendCode(phone, code)
        verifyNoInteractions(sms)
    }

    @Test
    fun `a channel bean replaces the built-in channel of the same name`() {
        val customSms = channel("sms")
        val sms = mock(SmsService::class.java)
        val service = service(null, sms, listOf(customSms), listOf("sms"))

        service.sendCode(phone, code)

        verify(customSms).sendCode(phone, code)
        verifyNoInteractions(sms)
    }

    @Test
    fun `two channel beans with one name fail at construction`() {
        val ex = assertThrows<IllegalStateException> {
            service(null, mock(SmsService::class.java), listOf(channel("telegram"), channel("telegram")))
        }
        assertEquals(true, ex.message!!.contains("telegram"))
    }

    @Test
    fun `an order naming no available channel fails at construction`() {
        val ex = assertThrows<IllegalStateException> {
            service(null, mock(SmsService::class.java), order = listOf("telegram", "whatsapp"))
        }
        assertEquals(true, ex.message!!.contains("app.auth.otp.delivery-order"))
    }

    @Test
    fun `falls back to SMS when WhatsApp bean is absent`() {
        val sms = mock(SmsService::class.java)
        val service = service(null, sms)

        service.sendCode(phone, code)

        verify(sms).sendCode(phone, code)
    }

    @Test
    fun `uses WhatsApp first and skips SMS on success`() {
        val whatsApp = mock(WhatsAppService::class.java)
        val sms = mock(SmsService::class.java)
        val service = service(whatsApp, sms)

        service.sendCode(phone, code)

        verify(whatsApp).sendCode(phone, code)
        verifyNoInteractions(sms)
    }

    @Test
    fun `falls back to SMS when WhatsApp throws RuntimeException`() {
        val whatsApp = mock(WhatsAppService::class.java)
        val sms = mock(SmsService::class.java)
        doThrow(RuntimeException("twilio boom")).`when`(whatsApp).sendCode(phone, code)
        val service = service(whatsApp, sms)

        service.sendCode(phone, code)

        verify(whatsApp).sendCode(phone, code)
        verify(sms).sendCode(phone, code)
    }

    @Test
    fun `propagates SMS failure after WhatsApp fallback`() {
        val whatsApp = mock(WhatsAppService::class.java)
        val sms = mock(SmsService::class.java)
        doThrow(RuntimeException("whatsapp down")).`when`(whatsApp).sendCode(phone, code)
        doThrow(IllegalStateException("sms also down")).`when`(sms).sendCode(phone, code)
        val service = service(whatsApp, sms)

        val ex = assertThrows<OtpDeliveryException> { service.sendCode(phone, code) }
        assertEquals(true, ex.message!!.contains("whatsapp (java.lang.RuntimeException)"), ex.message)
        assertEquals(true, ex.message!!.contains("sms (java.lang.IllegalStateException)"), ex.message)
        verify(whatsApp).sendCode(phone, code)
        verify(sms).sendCode(phone, code)
    }

    @Test
    fun `propagates SMS failure when no WhatsApp configured`() {
        val sms = mock(SmsService::class.java)
        doThrow(IllegalStateException("sms down")).`when`(sms).sendCode(phone, code)
        val service = service(null, sms)

        assertThrows<OtpDeliveryException> { service.sendCode(phone, code) }
        verify(sms).sendCode(phone, code)
    }

    @Test
    fun `does NOT catch Error from WhatsApp`() {
        // Errors (OutOfMemory, etc.) must NOT trigger SMS fallback — propagate so
        // ops can see the real cause instead of silently masking it as a WhatsApp failure.
        val whatsApp = mock(WhatsAppService::class.java)
        val sms = mock(SmsService::class.java)
        doThrow(OutOfMemoryError("simulated")).`when`(whatsApp).sendCode(phone, code)
        val service = service(whatsApp, sms)

        assertThrows<OutOfMemoryError> { service.sendCode(phone, code) }
        verify(sms, never()).sendCode(phone, code)
    }
}
