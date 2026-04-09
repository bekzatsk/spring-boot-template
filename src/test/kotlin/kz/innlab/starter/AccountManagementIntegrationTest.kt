package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.repository.SmsVerificationRepository
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
class AccountManagementIntegrationTest {

    @MockitoBean
    private lateinit var emailService: EmailService

    @MockitoBean
    private lateinit var smsService: SmsService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @Autowired
    private lateinit var smsVerificationRepository: SmsVerificationRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        verificationCodeRepository.deleteAll()
        smsVerificationRepository.deleteAll()
        userRepository.deleteAll()
    }

    // --- Helpers ---

    private fun createLocalUser(email: String = "test@example.com", password: String = "OldPassword123"): User {
        val user = User(email = email).also {
            it.providers.add(AuthProvider.LOCAL)
            it.passwordHash = passwordEncoder.encode(password)
        }
        return userRepository.save(user)
    }

    private fun generateAccessToken(user: User): String {
        return tokenService.generateAccessToken(user.id, user.roles)
    }

    private fun captureEmailCodeOnSend(): () -> String {
        var capturedCode: String? = null
        doAnswer { invocation ->
            capturedCode = invocation.arguments[1] as String
            null
        }.`when`(emailService).sendCode(anyString(), anyString(), anyString())
        return { capturedCode ?: error("emailService.sendCode was not called") }
    }

    private fun capturePhoneCodeOnSend(): () -> String {
        var capturedCode: String? = null
        doAnswer { invocation ->
            capturedCode = invocation.arguments[1] as String
            null
        }.`when`(smsService).sendCode(anyString(), anyString())
        return { capturedCode ?: error("smsService.sendCode was not called") }
    }

    private fun extractVerificationId(result: MvcResult): String {
        val body = result.response.contentAsString
        val mapper = JsonMapper.builder().build()
        val tree = mapper.readTree(body)
        return tree.get("verificationId").asText()
    }

    // --- Forgot Password Tests ---

    @Test
    fun `forgot password success resets password and revokes tokens`() {
        val user = createLocalUser()
        refreshTokenService.createToken(user)

        val getCode = captureEmailCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.verificationId").exists())
            .andReturn()

        val verificationId = extractVerificationId(requestResult)
        val code = getCode()

        mockMvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "$verificationId", "email": "test@example.com", "code": "$code", "newPassword": "NewPassword456"}""")
        )
            .andExpect(status().isOk)

        // Verify password was changed
        val updatedUser = userRepository.findById(user.id).orElseThrow()
        assert(passwordEncoder.matches("NewPassword456", updatedUser.passwordHash)) { "Password should be updated" }

        // Verify all refresh tokens revoked
        assert(refreshTokenRepository.findAll().isEmpty()) { "All refresh tokens should be deleted" }
    }

    @Test
    fun `forgot password unknown email returns 202 with null verificationId`() {
        doNothing().`when`(emailService).sendCode(anyString(), anyString(), anyString())

        mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "unknown@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.verificationId").doesNotExist())

        // EmailService should NOT have been called
        verify(emailService, never()).sendCode(anyString(), anyString(), anyString())
    }

    @Test
    fun `reset password with wrong code returns 401`() {
        createLocalUser()

        val getCode = captureEmailCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andReturn()

        val verificationId = extractVerificationId(requestResult)
        // ignore getCode() — use wrong code instead

        mockMvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "$verificationId", "email": "test@example.com", "code": "000000", "newPassword": "NewPassword456"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // --- Change Password Tests ---

    @Test
    fun `change password success updates hash and revokes tokens`() {
        val user = createLocalUser()
        val accessToken = generateAccessToken(user)
        refreshTokenService.createToken(user)

        mockMvc.perform(
            post("/api/v1/users/me/change-password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "OldPassword123", "newPassword": "NewPassword456"}""")
        )
            .andExpect(status().isOk)

        val updatedUser = userRepository.findById(user.id).orElseThrow()
        assert(passwordEncoder.matches("NewPassword456", updatedUser.passwordHash)) { "Password should be updated" }
        assert(refreshTokenRepository.findAll().isEmpty()) { "All refresh tokens should be deleted" }
    }

    @Test
    fun `change password with wrong current password returns 401`() {
        val user = createLocalUser()
        val accessToken = generateAccessToken(user)

        mockMvc.perform(
            post("/api/v1/users/me/change-password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "WrongPassword", "newPassword": "NewPassword456"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `change password for social-only user returns 409`() {
        val user = User(email = "social@example.com").also {
            it.providers.add(AuthProvider.GOOGLE)
        }
        userRepository.save(user)
        val accessToken = generateAccessToken(user)

        mockMvc.perform(
            post("/api/v1/users/me/change-password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "anything", "newPassword": "NewPassword456"}""")
        )
            .andExpect(status().isConflict)
    }

    // --- Change Email Tests ---

    @Test
    fun `change email success updates email`() {
        val user = createLocalUser(email = "old@example.com")
        val accessToken = generateAccessToken(user)

        val getCode = captureEmailCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail": "new@example.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationId").exists())
            .andReturn()

        val verificationId = extractVerificationId(requestResult)
        val code = getCode()

        mockMvc.perform(
            post("/api/v1/users/me/change-email/verify")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "$verificationId", "code": "$code"}""")
        )
            .andExpect(status().isOk)

        val updatedUser = userRepository.findById(user.id).orElseThrow()
        assert(updatedUser.email == "new@example.com") { "Email should be updated to new@example.com" }
    }

    @Test
    fun `change email request rejects already-taken email`() {
        val user1 = createLocalUser(email = "user1@example.com")
        createLocalUser(email = "user2@example.com", password = "Password123")
        val accessToken = generateAccessToken(user1)

        doNothing().`when`(emailService).sendCode(anyString(), anyString(), anyString())

        mockMvc.perform(
            post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail": "user2@example.com"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `change email verify rejects email taken between request and verify`() {
        val user = createLocalUser(email = "user@example.com")
        val accessToken = generateAccessToken(user)

        val getCode = captureEmailCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail": "contested@example.com"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val verificationId = extractVerificationId(requestResult)
        val code = getCode()

        // Simulate race condition: another user takes the email between request and verify
        userRepository.save(User(email = "contested@example.com").also {
            it.providers.add(AuthProvider.LOCAL)
        })

        mockMvc.perform(
            post("/api/v1/users/me/change-email/verify")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "$verificationId", "code": "$code"}""")
        )
            .andExpect(status().isConflict)
    }

    // --- Change Phone Tests ---

    @Test
    fun `change phone success updates phone`() {
        val user = createLocalUser(email = "phone-user@example.com")
        val accessToken = generateAccessToken(user)

        val getCode = capturePhoneCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/users/me/change-phone/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "+77009876543"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationId").exists())
            .andReturn()

        val verificationId = extractVerificationId(requestResult)
        val code = getCode()

        mockMvc.perform(
            post("/api/v1/users/me/change-phone/verify")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "$verificationId", "phone": "+77009876543", "code": "$code"}""")
        )
            .andExpect(status().isOk)

        val updatedUser = userRepository.findById(user.id).orElseThrow()
        assert(updatedUser.phone == "+77009876543") { "Phone should be updated" }
        assert(AuthProvider.LOCAL in updatedUser.providers) { "LOCAL provider should be present" }
    }

    @Test
    fun `change phone request rejects already-taken phone`() {
        val existingPhoneUser = User(email = "existing-phone@example.com").also {
            it.providers.add(AuthProvider.LOCAL)
            it.phone = "+77001111111"
        }
        userRepository.save(existingPhoneUser)

        val user = createLocalUser(email = "wants-phone@example.com")
        val accessToken = generateAccessToken(user)

        doNothing().`when`(smsService).sendCode(anyString(), anyString())

        mockMvc.perform(
            post("/api/v1/users/me/change-phone/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "+77001111111"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `change phone with invalid phone format returns 400`() {
        val user = createLocalUser()
        val accessToken = generateAccessToken(user)

        mockMvc.perform(
            post("/api/v1/users/me/change-phone/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "not-a-number"}""")
        )
            .andExpect(status().isBadRequest)
    }

    // --- Rate Limiting Test ---

    @Test
    fun `verification code rate limited returns 409`() {
        createLocalUser(email = "rate-test@example.com")

        doNothing().`when`(emailService).sendCode(anyString(), anyString(), anyString())

        mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "rate-test@example.com"}""")
        )
            .andExpect(status().isAccepted)

        // Immediate second request should hit rate limit
        mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "rate-test@example.com"}""")
        )
            .andExpect(status().isConflict)
    }

    // --- Endpoint Protection Test ---

    @Test
    fun `change password without Bearer token returns 401`() {
        mockMvc.perform(
            post("/api/v1/users/me/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "anything", "newPassword": "anything"}""")
        )
            .andExpect(status().isUnauthorized)
    }
}
