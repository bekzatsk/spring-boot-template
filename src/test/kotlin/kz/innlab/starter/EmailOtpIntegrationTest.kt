package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
class EmailOtpIntegrationTest {

    @MockitoBean
    private lateinit var emailService: EmailService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    private val email = "otp@example.com"

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        verificationCodeRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun captureCodeOnSend(): () -> String {
        var capturedCode: String? = null
        doAnswer { invocation ->
            capturedCode = invocation.arguments[1] as String
            null
        }.`when`(emailService).sendCode(anyString(), anyString(), anyString())
        return { capturedCode ?: error("emailService.sendCode was not called") }
    }

    private fun verificationId(result: MvcResult): String =
        JsonMapper.builder().build().readTree(result.response.contentAsString)
            .get("verificationId").asText()

    @Test
    fun `email OTP creates passwordless user and returns tokens`() {
        val getCode = captureCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/auth/email/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"OTP@Example.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationId").exists())
            .andReturn()

        val code = getCode()
        assert(code.length == 6)

        mockMvc.perform(
            post("/api/v1/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"${verificationId(requestResult)}","email":"otp@example.com","code":"$code"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        val user = userRepository.findByEmail(email)
        assert(user != null)
        assert(user!!.passwordHash == null)
        assert(user.emailVerified)
        assert(AuthProvider.LOCAL in user.providers)
    }

    @Test
    fun `email OTP verifies and unlocks existing user`() {
        userRepository.save(User(email).also {
            it.linkProvider(AuthProvider.LOCAL)
            it.emailVerified = false
            it.requiredActions.add(RequiredAction.VERIFY_EMAIL)
        })
        val getCode = captureCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/auth/email/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isOk).andReturn()

        mockMvc.perform(
            post("/api/v1/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"${verificationId(requestResult)}","email":"$email","code":"${getCode()}"}""")
        ).andExpect(status().isOk)

        val user = requireNotNull(userRepository.findByEmail(email))
        assert(user.emailVerified)
        assert(RequiredAction.VERIFY_EMAIL !in user.requiredActions)
        assert(userRepository.count() == 1L)
    }

    @Test
    fun `email OTP rejects wrong code`() {
        val getCode = captureCodeOnSend()
        val requestResult = mockMvc.perform(
            post("/api/v1/auth/email/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isOk).andReturn()

        val wrongCode = if (getCode() == "000000") "111111" else "000000"
        mockMvc.perform(
            post("/api/v1/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"${verificationId(requestResult)}","email":"$email","code":"$wrongCode"}""")
        ).andExpect(status().isUnauthorized)

        assert(userRepository.findByEmail(email) == null)
    }
}
