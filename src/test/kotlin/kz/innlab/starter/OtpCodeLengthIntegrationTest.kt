package kz.innlab.starter

import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.SmsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = [
    "app.auth.phone.code-length=4",
    "app.auth.email-otp.code-length=6"
])
@AutoConfigureMockMvc
class OtpCodeLengthIntegrationTest {

    @MockitoBean
    private lateinit var smsService: SmsService

    @MockitoBean
    private lateinit var emailService: EmailService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @BeforeEach
    fun cleanUp() = verificationCodeRepository.deleteAll()

    @Test
    fun `phone and email OTP lengths are configured independently`() {
        var phoneCode: String? = null
        var emailCode: String? = null
        doAnswer { invocation ->
            phoneCode = invocation.arguments[1] as String
            null
        }.`when`(smsService).sendCode(anyString(), anyString())
        doAnswer { invocation ->
            emailCode = invocation.arguments[1] as String
            null
        }.`when`(emailService).sendCode(anyString(), anyString(), anyString())

        mockMvc.perform(
            post("/api/v1/auth/phone/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone":"+77001234567"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/auth/email/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"otp-length@example.com"}""")
        ).andExpect(status().isOk)

        assert(phoneCode?.length == 4) { "Expected 4-digit phone OTP, got $phoneCode" }
        assert(emailCode?.length == 6) { "Expected 6-digit email OTP, got $emailCode" }
    }
}
