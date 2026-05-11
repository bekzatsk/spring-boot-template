package kz.innlab.starter

import kz.innlab.starter.authentication.model.TelegramSessionStatus
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.repository.TelegramAuthSessionRepository
import kz.innlab.starter.authentication.service.TelegramBotService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
class TelegramAuthIntegrationTest {

    @MockitoBean
    private lateinit var telegramBotService: TelegramBotService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var telegramAuthSessionRepository: TelegramAuthSessionRepository

    private val mapper = JsonMapper.builder().build()

    private val testTelegramUserId = 123456789L
    private val testTelegramUsername = "aidana_m"
    private val testChatId = 123456789L

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        telegramAuthSessionRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun extractSessionId(responseBody: String): String {
        val tree = mapper.readTree(responseBody)
        return tree.get("sessionId").asText()
    }

    private fun captureCodeOnBotSend(): () -> String {
        var capturedCode: String? = null
        doAnswer { invocation ->
            val text = invocation.arguments[1] as String
            val match = Regex("\\d{6}").find(text)
            capturedCode = match?.value
            null
        }.`when`(telegramBotService).sendMessage(anyLong(), anyString())
        return { capturedCode ?: error("telegramBotService.sendMessage was not called with a code") }
    }

    private fun simulateWebhookStart(sessionId: String) {
        val webhookBody = """
        {
            "update_id": 1,
            "message": {
                "message_id": 1,
                "from": {
                    "id": $testTelegramUserId,
                    "is_bot": false,
                    "first_name": "Aidana",
                    "username": "$testTelegramUsername"
                },
                "chat": {
                    "id": $testChatId,
                    "first_name": "Aidana",
                    "type": "private"
                },
                "date": 1700000000,
                "text": "/start $sessionId"
            }
        }
        """.trimIndent()

        mockMvc.perform(
            post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                .content(webhookBody)
        ).andExpect(status().isOk)
    }

    @Test
    fun `init session returns 201 with sessionId and botUrl`() {
        mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sessionId").exists())
            .andExpect(jsonPath("$.botUrl").exists())
            .andExpect(jsonPath("$.botUsername").value("TestBot"))
            .andExpect(jsonPath("$.expiresAt").exists())
    }

    @Test
    fun `full flow - init, webhook start, verify code, get tokens`() {
        val getCode = captureCodeOnBotSend()

        // Step 1: Init session
        val initResult = mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isCreated)
            .andReturn()

        val sessionId = extractSessionId(initResult.response.contentAsString)

        // Step 2: Check status — should be pending
        mockMvc.perform(get("/api/v1/auth/telegram/status/$sessionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.telegramConnected").value(false))

        // Step 3: Simulate Telegram webhook /start
        simulateWebhookStart(sessionId)

        val code = getCode()

        // Step 4: Check status — should be code_sent
        mockMvc.perform(get("/api/v1/auth/telegram/status/$sessionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("code_sent"))
            .andExpect(jsonPath("$.telegramConnected").value(true))

        // Step 5: Verify code
        mockMvc.perform(
            post("/api/v1/auth/telegram/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId", "code": "$code"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.telegramUserId").value(testTelegramUserId))
            .andExpect(jsonPath("$.telegramUsername").value(testTelegramUsername))
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        // User created with TELEGRAM provider
        val user = userRepository.findByTelegramUserId(testTelegramUserId)
        assert(user != null) { "Telegram user should be created in DB" }
        assert(AuthProvider.TELEGRAM in user!!.providers) { "Providers should contain TELEGRAM" }
        assert(user.telegramUserId == testTelegramUserId) { "telegramUserId should match" }
        assert(user.telegramUsername == testTelegramUsername) { "telegramUsername should match" }
    }

    @Test
    fun `verify with wrong code returns 400 with attempts left`() {
        doNothing().`when`(telegramBotService).sendMessage(anyLong(), anyString())

        // Init + webhook
        val initResult = mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isCreated).andReturn()

        val sessionId = extractSessionId(initResult.response.contentAsString)
        simulateWebhookStart(sessionId)

        // Wrong code
        mockMvc.perform(
            post("/api/v1/auth/telegram/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId", "code": "000000"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.error").value("INVALID_CODE"))
            .andExpect(jsonPath("$.attemptsLeft").value(2))
    }

    @Test
    fun `verify with max attempts returns 429`() {
        doNothing().`when`(telegramBotService).sendMessage(anyLong(), anyString())

        val initResult = mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isCreated).andReturn()

        val sessionId = extractSessionId(initResult.response.contentAsString)
        simulateWebhookStart(sessionId)

        // Exhaust 3 attempts
        repeat(3) {
            mockMvc.perform(
                post("/api/v1/auth/telegram/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"sessionId": "$sessionId", "code": "000000"}""")
            )
        }

        // 4th attempt
        mockMvc.perform(
            post("/api/v1/auth/telegram/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId", "code": "000000"}""")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.error").value("MAX_ATTEMPTS"))
    }

    @Test
    fun `verify before webhook start returns error`() {
        val initResult = mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isCreated).andReturn()

        val sessionId = extractSessionId(initResult.response.contentAsString)

        mockMvc.perform(
            post("/api/v1/auth/telegram/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId", "code": "123456"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.error").value("CODE_NOT_SENT"))
    }

    @Test
    fun `resend code success`() {
        val getCode = captureCodeOnBotSend()

        val initResult = mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isCreated).andReturn()

        val sessionId = extractSessionId(initResult.response.contentAsString)
        simulateWebhookStart(sessionId)

        // Resend
        mockMvc.perform(
            post("/api/v1/auth/telegram/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sent").value(true))
            .andExpect(jsonPath("$.cooldown").value(60))

        // Verify with new code
        val newCode = getCode()
        mockMvc.perform(
            post("/api/v1/auth/telegram/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId", "code": "$newCode"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))
    }

    @Test
    fun `returning telegram user finds existing user`() {
        // Pre-create a Telegram user
        val existingUser = userRepository.save(
            User(email = "").also {
                it.providers.add(AuthProvider.TELEGRAM)
                it.providerIds[AuthProvider.TELEGRAM] = testTelegramUserId.toString()
                it.telegramUserId = testTelegramUserId
                it.telegramUsername = testTelegramUsername
            }
        )

        val getCode = captureCodeOnBotSend()

        val initResult = mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isCreated).andReturn()

        val sessionId = extractSessionId(initResult.response.contentAsString)
        simulateWebhookStart(sessionId)
        val code = getCode()

        mockMvc.perform(
            post("/api/v1/auth/telegram/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId", "code": "$code"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))

        // No duplicate user created
        val users = userRepository.findAll().filter { it.telegramUserId == testTelegramUserId }
        assert(users.size == 1) { "Should be exactly 1 Telegram user, found ${users.size}" }
        assert(users[0].id == existingUser.id) { "Should be the same existing user" }
    }

    @Test
    fun `webhook with invalid secret is ignored`() {
        doNothing().`when`(telegramBotService).sendMessage(anyLong(), anyString())

        val initResult = mockMvc.perform(
            post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isCreated).andReturn()

        val sessionId = extractSessionId(initResult.response.contentAsString)

        val webhookBody = """
        {
            "update_id": 1,
            "message": {
                "message_id": 1,
                "from": {"id": $testTelegramUserId, "is_bot": false, "first_name": "Test"},
                "chat": {"id": $testChatId, "first_name": "Test", "type": "private"},
                "date": 1700000000,
                "text": "/start $sessionId"
            }
        }
        """.trimIndent()

        // Send with wrong secret
        mockMvc.perform(
            post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                .content(webhookBody)
        ).andExpect(status().isOk)

        // Session should still be pending — webhook was rejected
        val session = telegramAuthSessionRepository.findBySessionId(sessionId)
        assert(session != null) { "Session should exist" }
        assert(session!!.status == TelegramSessionStatus.PENDING) { "Session should remain pending after invalid webhook" }
    }
}
