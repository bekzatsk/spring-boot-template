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

        val ex = assertThrows<IllegalStateException> { service.sendCode(phone, code) }
        assertEquals("sms also down", ex.message)
        verify(whatsApp).sendCode(phone, code)
        verify(sms).sendCode(phone, code)
    }

    @Test
    fun `propagates SMS failure when no WhatsApp configured`() {
        val sms = mock(SmsService::class.java)
        doThrow(IllegalStateException("sms down")).`when`(sms).sendCode(phone, code)
        val service = service(null, sms)

        assertThrows<IllegalStateException> { service.sendCode(phone, code) }
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
