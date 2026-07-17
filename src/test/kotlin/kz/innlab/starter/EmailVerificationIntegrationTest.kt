package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(properties = ["app.auth.email-verification.enabled=true"])
@AutoConfigureMockMvc
class EmailVerificationIntegrationTest {

    @MockitoBean private lateinit var emailService: EmailService
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var verificationCodeRepository: VerificationCodeRepository
    @Autowired private lateinit var tokenService: TokenService

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        verificationCodeRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun captureEmailCodeOnSend(): () -> String {
        var captured: String? = null
        doAnswer { inv ->
            captured = inv.arguments[1] as String
            null
        }.`when`(emailService).sendCode(anyString(), anyString(), anyString())
        return { captured ?: error("emailService.sendCode was not called") }
    }

    private fun register(email: String): MvcResult =
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "$email", "password": "SecurePass123"}""")
        ).andReturn()

    private fun jsonField(result: MvcResult, field: String): String =
        JsonMapper.builder().build().readTree(result.response.contentAsString).get(field).asText()

    @Test
    fun `register emits VERIFY_EMAIL action, sets emailVerified false, sends code`() {
        val getCode = captureEmailCodeOnSend()

        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "verify@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.requiredActions[0]").value("VERIFY_EMAIL"))

        val user = userRepository.findByEmail("verify@example.com")!!
        assert(!user.emailVerified) { "new user must be unverified" }
        assert(RequiredAction.VERIFY_EMAIL in user.requiredActions)
        assert(getCode().matches(Regex("\\d{6}"))) { "a 6-digit code must be sent" }
    }

    @Test
    fun `protected endpoint blocked with 403 until email verified`() {
        register("gate@example.com")
        val user = userRepository.findByEmail("gate@example.com")!!

        // Token issued at registration carries VERIFY_EMAIL → RequiredActionFilter blocks.
        // /users/me is allowlisted, so hit a non-allowlisted protected endpoint.
        val token = tokenService.generateAccessToken(user.id, user.roles, user.requiredActions)
        mockMvc.perform(
            get("/api/v1/notifications/tokens").header("Authorization", "Bearer $token")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `verify-email clears action and unlocks access`() {
        val getCode = captureEmailCodeOnSend()
        val reg = register("unlock@example.com")
        val code = getCode()
        val user = userRepository.findByEmail("unlock@example.com")!!
        val verificationId = verificationCodeRepository.findAll().first { it.identifier == "unlock@example.com" }.id

        mockMvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "unlock@example.com", "verificationId": "$verificationId", "code": "$code"}""")
        )
            .andExpect(status().isOk)

        val updated = userRepository.findByEmail("unlock@example.com")!!
        assert(updated.emailVerified) { "user must be verified after verify-email" }
        assert(RequiredAction.VERIFY_EMAIL !in updated.requiredActions)

        // Clean token (no required_actions) now reaches the protected endpoint
        val cleanToken = tokenService.generateAccessToken(updated.id, updated.roles, updated.requiredActions)
        mockMvc.perform(
            get("/api/v1/users/me").header("Authorization", "Bearer $cleanToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailVerified").value(true))
    }

    @Test
    fun `verify-email with wrong code returns 401`() {
        register("wrong@example.com")
        val verificationId = verificationCodeRepository.findAll().first { it.identifier == "wrong@example.com" }.id

        mockMvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "wrong@example.com", "verificationId": "$verificationId", "code": "000000"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `resend for unknown email returns 202 with null verificationId (anti-enumeration)`() {
        mockMvc.perform(
            post("/api/v1/auth/verify-email/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "ghost@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.verificationId").doesNotExist())

        verify(emailService, never()).sendCode(anyString(), anyString(), anyString())
    }
}

/**
 * Backward-compat: with email-verification disabled (default), registration is unchanged —
 * no VERIFY_EMAIL action, user is verified, no email sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationDisabledIntegrationTest {

    @MockitoBean private lateinit var emailService: EmailService
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `register does not gate or send email when verification disabled`() {
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "plain@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.requiredActions").isEmpty)

        val user = userRepository.findByEmail("plain@example.com")!!
        assert(user.emailVerified) { "verification disabled → user stays verified" }
        verify(emailService, never()).sendCode(anyString(), anyString(), anyString())
    }
}
