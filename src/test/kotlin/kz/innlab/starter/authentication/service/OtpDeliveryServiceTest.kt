package kz.innlab.starter.authentication.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.util.Optional
import kotlin.test.assertEquals

class OtpDeliveryServiceTest {

    private val phone = "+15551234567"
    private val code = "123456"

    @Test
    fun `falls back to SMS when WhatsApp bean is absent`() {
        val sms = mock(SmsService::class.java)
        val service = OtpDeliveryService(Optional.empty(), sms)

        service.sendCode(phone, code)

        verify(sms).sendCode(phone, code)
    }

    @Test
    fun `uses WhatsApp first and skips SMS on success`() {
        val whatsApp = mock(WhatsAppService::class.java)
        val sms = mock(SmsService::class.java)
        val service = OtpDeliveryService(Optional.of(whatsApp), sms)

        service.sendCode(phone, code)

        verify(whatsApp).sendCode(phone, code)
        verifyNoInteractions(sms)
    }

    @Test
    fun `falls back to SMS when WhatsApp throws RuntimeException`() {
        val whatsApp = mock(WhatsAppService::class.java)
        val sms = mock(SmsService::class.java)
        doThrow(RuntimeException("twilio boom")).`when`(whatsApp).sendCode(phone, code)
        val service = OtpDeliveryService(Optional.of(whatsApp), sms)

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
        val service = OtpDeliveryService(Optional.of(whatsApp), sms)

        val ex = assertThrows<IllegalStateException> { service.sendCode(phone, code) }
        assertEquals("sms also down", ex.message)
        verify(whatsApp).sendCode(phone, code)
        verify(sms).sendCode(phone, code)
    }

    @Test
    fun `propagates SMS failure when no WhatsApp configured`() {
        val sms = mock(SmsService::class.java)
        doThrow(IllegalStateException("sms down")).`when`(sms).sendCode(phone, code)
        val service = OtpDeliveryService(Optional.empty(), sms)

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
        val service = OtpDeliveryService(Optional.of(whatsApp), sms)

        assertThrows<OutOfMemoryError> { service.sendCode(phone, code) }
        verify(sms, never()).sendCode(phone, code)
    }
}
