package kz.innlab.starter

import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.notification.service.PushService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Toll fraud: the per-number cooldown does nothing against a client walking through numbers.
 * The per-client limit does.
 */
@SpringBootTest(
    properties = [
        "app.auth.rate-limit.code-send-per-client.max-attempts=3",
        "app.auth.rate-limit.code-send-per-client.window-seconds=3600"
    ]
)
@AutoConfigureMockMvc
class OtpSendLimitIntegrationTest {

    @MockitoBean private lateinit var pushService: PushService
    @MockitoBean private lateinit var smsService: SmsService
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var verificationCodeRepository: VerificationCodeRepository

    private fun requestOtp(phone: String, client: String) = mockMvc.perform(
        post("/api/v1/auth/phone/request")
            .with { it.remoteAddr = client; it }
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"phone": "$phone"}""")
    )

    @Test
    fun `one client cannot send codes to an unbounded list of numbers`() {
        verificationCodeRepository.deleteAll()
        listOf("+77001000001", "+77001000002", "+77001000003").forEach {
            requestOtp(it, "203.0.113.7").andExpect(status().isOk)
        }

        requestOtp("+77001000004", "203.0.113.7")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
        verify(smsService, times(3)).sendCode(anyString(), anyString())

        // Another client is unaffected.
        requestOtp("+77001000005", "198.51.100.9").andExpect(status().isOk)
    }
}
