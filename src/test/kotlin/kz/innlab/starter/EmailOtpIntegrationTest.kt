package kz.innlab.starter

import kz.innlab.starter.notification.repository.DeviceTokenRepository
import kz.innlab.starter.notification.model.Platform
import kz.innlab.starter.notification.model.DeviceToken
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

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    private val email = "otp@example.com"

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        verificationCodeRepository.deleteAll()
        deviceTokenRepository.deleteAll()
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
    fun `email OTP evicts whoever pre-registered the address without verifying it`() {
        val getCode = captureCodeOnSend()

        // Attacker registers the victim's address with their own password and keeps the tokens.
        val registerResult = mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"AttackerPassword123"}""")
        ).andExpect(status().isCreated).andReturn()
        val registered = JsonMapper.builder().build().readTree(registerResult.response.contentAsString)
        val attackerRefreshToken = registered.get("refreshToken").asText()
        val attackerAccess = registered.get("accessToken").asText()

        // The registrant cannot attach a phone before the address is verified...
        mockMvc.perform(
            post("/api/v1/users/me/change-phone/request")
                .header("Authorization", "Bearer $attackerAccess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"AttackerPassword123","phone":"+77005550000"}""")
        ).andExpect(status().isForbidden)
        // ...and a push device registered now does not survive the owner's claim.
        val attackerId = requireNotNull(userRepository.findByEmail(email)).id
        deviceTokenRepository.save(DeviceToken(attackerId, Platform.ANDROID, "attacker-device", "attacker-phone"))

        // The real owner signs in with a code sent to the address.
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
        assert(user.passwordHash == null) { "registrant's password must not survive ownership proof" }
        assert(deviceTokenRepository.findByUserId(user.id).isEmpty()) { "registrant's devices must be dropped" }

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$attackerRefreshToken"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"AttackerPassword123"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `email OTP keeps the password of an already verified user`() {
        userRepository.save(User(email).also {
            it.linkProvider(AuthProvider.LOCAL)
            it.passwordHash = "existing-hash"
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

        assert(requireNotNull(userRepository.findByEmail(email)).passwordHash == "existing-hash")
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
