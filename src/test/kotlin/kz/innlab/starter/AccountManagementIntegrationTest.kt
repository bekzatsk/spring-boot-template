package kz.innlab.starter

import kz.innlab.starter.shared.ratelimit.RateLimiter
import java.time.Duration
import org.awaitility.Awaitility.await
import java.time.Instant
import org.springframework.security.oauth2.jwt.JwtDecoder
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
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
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var rateLimiter: RateLimiter

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        verificationCodeRepository.deleteAll()
        userRepository.deleteAll()
        // The request cooldown lives in the shared in-memory limiter, not in the wiped tables.
        listOf("test@example.com", "unknown@example.com", "rate-test@example.com").forEach { email ->
            rateLimiter.reset("code-request:FORGOT_PASSWORD:$email")
        }
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
        // Reset and resend codes are sent off the request thread.
        return {
            await().atMost(Duration.ofSeconds(5)).until { capturedCode != null }
            capturedCode!!
        }
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
        return tree.get("verificationId").asString()
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
    fun `forgot password unknown email answers like a known one`() {
        doNothing().`when`(emailService).sendCode(anyString(), anyString(), anyString())

        mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "unknown@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.verificationId").exists())

        // Same cooldown as a real account, or a second request would tell them apart.
        mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "unknown@example.com"}""")
        )
            .andExpect(status().isConflict)

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

    @Test
    fun `reset password stops accepting the correct code after the attempt limit`() {
        // The attempt counter used to be rolled back together with the caller's transaction,
        // so the limit never applied. It is now committed independently.
        val user = createLocalUser()
        val getCode = captureEmailCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@example.com"}""")
        )
            .andExpect(status().isAccepted)
            .andReturn()

        val verificationId = extractVerificationId(requestResult)
        val correctCode = getCode()

        repeat(3) {
            mockMvc.perform(
                post("/api/v1/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"verificationId": "$verificationId", "email": "test@example.com", "code": "000000", "newPassword": "NewPassword456"}""")
            )
                .andExpect(status().isUnauthorized)
        }

        mockMvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "$verificationId", "email": "test@example.com", "code": "$correctCode", "newPassword": "NewPassword456"}""")
        )
            .andExpect(status().isUnauthorized)

        val unchanged = userRepository.findById(user.id).orElseThrow()
        assert(passwordEncoder.matches("OldPassword123", unchanged.passwordHash)) {
            "Password must stay unchanged once the attempt limit is exhausted"
        }
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
    fun `change password rejects a password shorter than the registration policy`() {
        val user = createLocalUser()
        val accessToken = generateAccessToken(user)

        mockMvc.perform(
            post("/api/v1/users/me/change-password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "OldPassword123", "newPassword": "short"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))

        // The weak password must not have been applied
        val unchanged = userRepository.findById(user.id).orElseThrow()
        assert(passwordEncoder.matches("OldPassword123", unchanged.passwordHash)) {
            "Password must stay unchanged when the new one is rejected"
        }
    }

    @Test
    fun `reset password rejects a password shorter than the registration policy`() {
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
        val code = getCode()

        mockMvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "$verificationId", "email": "test@example.com", "code": "$code", "newPassword": "short"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
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
                .content("""{"currentPassword": "OldPassword123", "newEmail": "new@example.com"}""")
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
                .content("""{"currentPassword": "OldPassword123", "newEmail": "user2@example.com"}""")
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
                .content("""{"currentPassword": "OldPassword123", "newEmail": "contested@example.com"}""")
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
                .content("""{"currentPassword": "OldPassword123", "phone": "+77009876543"}""")
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
    fun `change phone code cannot verify a different number`() {
        val user = createLocalUser(email = "phone-binding@example.com")
        val accessToken = generateAccessToken(user)
        val getCode = capturePhoneCodeOnSend()
        val requestResult = mockMvc.perform(
            post("/api/v1/users/me/change-phone/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "OldPassword123", "phone":"+77009876543"}""")
        ).andExpect(status().isOk).andReturn()

        val verificationId = extractVerificationId(requestResult)
        val code = getCode()
        mockMvc.perform(
            post("/api/v1/users/me/change-phone/verify")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"$verificationId","phone":"+77009876544","code":"$code"}""")
        ).andExpect(status().isBadRequest)

        assert(userRepository.findById(user.id).orElseThrow().phone == null)
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
                .content("""{"currentPassword": "OldPassword123", "phone": "+77001111111"}""")
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
                .content("""{"currentPassword": "OldPassword123", "phone": "not-a-number"}""")
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

    // --- Re-authentication before identity changes ---

    @Test
    fun `change email without proof of ownership returns 403`() {
        val user = createLocalUser(email = "victim@example.com")
        mockMvc.perform(
            post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer ${generateAccessToken(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail": "attacker@example.com"}""")
        ).andExpect(status().isForbidden)
        verify(emailService, never()).sendCode(anyString(), anyString(), anyString())
    }

    @Test
    fun `change phone with a wrong password returns 401`() {
        val user = createLocalUser(email = "victim-phone@example.com")
        mockMvc.perform(
            post("/api/v1/users/me/change-phone/request")
                .header("Authorization", "Bearer ${generateAccessToken(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "guess", "phone": "+77009876543"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `passwordless account proves ownership with a code sent to its current email`() {
        val user = userRepository.save(User(email = "otp-only@example.com").also {
            it.linkProvider(AuthProvider.LOCAL)
        })
        val accessToken = generateAccessToken(user)
        val getCode = captureEmailCodeOnSend()

        val reauth = mockMvc.perform(
            post("/api/v1/users/me/reauth/request").header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.channel").value("EMAIL"))
            .andReturn()
        verify(emailService).sendCode("otp-only@example.com", getCode(), "REAUTH")

        mockMvc.perform(
            post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail": "moved@example.com", "reauthVerificationId": "${extractVerificationId(reauth)}", "reauthCode": "${getCode()}"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `account with no password, email or phone may add one only right after logging in`() {
        val user = userRepository.save(User(email = "").also { it.telegramUserId = 4242L })
        val stale = tokenService.generateAccessToken(user.id, user.roles, authTime = Instant.now().minusSeconds(3600))
        val fresh = tokenService.generateAccessToken(user.id, user.roles)
        doNothing().`when`(emailService).sendCode(anyString(), anyString(), anyString())

        val request = { token: String ->
            mockMvc.perform(
                post("/api/v1/users/me/change-email/request")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"newEmail": "first@example.com"}""")
            )
        }
        request(stale).andExpect(status().isForbidden)
        request(fresh).andExpect(status().isOk)
    }

    @Test
    fun `changing email ends every session`() {
        val user = createLocalUser(email = "sessions@example.com")
        val refreshToken = refreshTokenService.createToken(user)
        val accessToken = generateAccessToken(user)
        val getCode = captureEmailCodeOnSend()

        val requestResult = mockMvc.perform(
            post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "OldPassword123", "newEmail": "sessions-new@example.com"}""")
        ).andExpect(status().isOk).andReturn()
        mockMvc.perform(
            post("/api/v1/users/me/change-email/verify")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId": "${extractVerificationId(requestResult)}", "code": "${getCode()}"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `refreshed access token keeps the original login time`() {
        val user = createLocalUser(email = "auth-time@example.com")
        val refreshToken = refreshTokenService.createToken(user)
        val loggedInAt = refreshTokenRepository.findAll().single().authenticatedAt

        val result = mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}""")
        ).andExpect(status().isOk).andReturn()
        val accessToken = JsonMapper.builder().build()
            .readTree(result.response.contentAsString).get("accessToken").asString()

        val authTime = jwtDecoder.decode(accessToken).claims["auth_time"] as Number
        assert(authTime.toLong() == loggedInAt.epochSecond)
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
