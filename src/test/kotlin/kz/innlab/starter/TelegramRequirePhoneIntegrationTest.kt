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
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
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

@SpringBootTest(properties = ["app.auth.telegram.require-phone=true"])
@AutoConfigureMockMvc
class TelegramRequirePhoneIntegrationTest {

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

    private val telegramUserId = 555000111L
    private val chatId = 555000111L
    private val phoneDigits = "77001234567"
    private val phoneE164 = "+77001234567"

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        telegramAuthSessionRepository.deleteAll()
        userRepository.deleteAll()
    }

    /** Captures the code from whichever send method delivers it. */
    private fun captureCode(): () -> String {
        var code: String? = null
        val capture = { text: String -> code = Regex("\\d{6}").find(text)?.value ?: code }
        doAnswer { capture(it.arguments[1] as String); null }
            .`when`(telegramBotService).sendMessage(anyLong(), anyString())
        doAnswer { capture(it.arguments[1] as String); null }
            .`when`(telegramBotService).sendMessageRemovingKeyboard(anyLong(), anyString())
        return { code ?: error("No code was sent") }
    }

    private fun initSession(): String {
        val body = mockMvc.perform(post("/api/v1/auth/telegram/init").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return mapper.readTree(body).get("sessionId").asString()
    }

    private fun webhook(messageJson: String) {
        mockMvc.perform(
            post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                .content(
                    """
                    {
                        "update_id": 1,
                        "message": {
                            "message_id": 1,
                            "from": {"id": $telegramUserId, "is_bot": false, "first_name": "Aidana", "username": "aidana_m"},
                            "chat": {"id": $chatId, "type": "private"},
                            "date": 1700000000,
                            $messageJson
                        }
                    }
                    """.trimIndent()
                )
        ).andExpect(status().isOk)
    }

    private fun start(sessionId: String) = webhook(""""text": "/start $sessionId"""")

    private fun shareContact(contactUserId: Long = telegramUserId, phone: String = phoneDigits) =
        webhook(""""contact": {"phone_number": "$phone", "first_name": "Aidana", "user_id": $contactUserId}""")

    private fun verifyCode(sessionId: String, code: String) =
        mockMvc.perform(
            post("/api/v1/auth/telegram/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId", "code": "$code"}""")
        )

    @Test
    fun `new user shares phone, gets code and is registered with that phone`() {
        val getCode = captureCode()
        val sessionId = initSession()

        start(sessionId)
        verify(telegramBotService).requestContact(eq(chatId), anyString(), anyString())
        verify(telegramBotService, never()).sendMessage(anyLong(), anyString())
        mockMvc.perform(get("/api/v1/auth/telegram/status/$sessionId"))
            .andExpect(jsonPath("$.status").value("phone_requested"))
            .andExpect(jsonPath("$.phoneRequired").value(true))

        shareContact()
        mockMvc.perform(get("/api/v1/auth/telegram/status/$sessionId"))
            .andExpect(jsonPath("$.status").value("code_sent"))
            .andExpect(jsonPath("$.phoneRequired").value(false))

        verifyCode(sessionId, getCode())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.accessToken").exists())

        val user = userRepository.findByTelegramUserId(telegramUserId)!!
        assert(user.phone == phoneE164) { "phone should be stored in E.164, was ${user.phone}" }
    }

    @Test
    fun `forwarded contact is rejected and the phone is asked again`() {
        captureCode()
        val sessionId = initSession()
        start(sessionId)

        shareContact(contactUserId = 999L)

        verify(telegramBotService, times(2)).requestContact(eq(chatId), anyString(), anyString())
        verify(telegramBotService, never()).sendMessageRemovingKeyboard(anyLong(), anyString())
        val session = telegramAuthSessionRepository.findBySessionId(sessionId)!!
        assert(session.status == TelegramSessionStatus.PHONE_REQUESTED)
        assert(session.phone == null)
    }

    @Test
    fun `text instead of contact asks for the phone again`() {
        captureCode()
        val sessionId = initSession()
        start(sessionId)

        webhook(""""text": "hello"""")

        verify(telegramBotService, times(2)).requestContact(eq(chatId), anyString(), anyString())
        verify(telegramBotService, never()).sendMessage(anyLong(), anyString())
    }

    @Test
    fun `resend before the phone is shared is refused`() {
        captureCode()
        val sessionId = initSession()
        start(sessionId)

        mockMvc.perform(
            post("/api/v1/auth/telegram/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId": "$sessionId"}""")
        ).andExpect(status().isConflict)
        verify(telegramBotService, never()).sendMessage(anyLong(), anyString())
    }

    @Test
    fun `phone of an existing phone user links Telegram to that user`() {
        val phoneUser = userRepository.save(User(email = "").also {
            it.linkProvider(AuthProvider.LOCAL)
            it.phone = phoneE164
        })
        val getCode = captureCode()
        val sessionId = initSession()
        start(sessionId)
        shareContact()

        verifyCode(sessionId, getCode()).andExpect(status().isOk)

        assert(userRepository.count() == 1L) { "no second account should be created" }
        val linked = userRepository.findById(phoneUser.id).orElseThrow()
        assert(linked.telegramUserId == telegramUserId)
        assert(AuthProvider.TELEGRAM in linked.providers && AuthProvider.LOCAL in linked.providers)
    }

    @Test
    fun `phone already linked to another Telegram account is refused`() {
        userRepository.save(User(email = "").also {
            it.linkProvider(AuthProvider.TELEGRAM, "42")
            it.telegramUserId = 42L
            it.phone = phoneE164
        })
        val getCode = captureCode()
        val sessionId = initSession()
        start(sessionId)
        shareContact()

        verifyCode(sessionId, getCode()).andExpect(status().isConflict)
        assert(userRepository.findByTelegramUserId(telegramUserId) == null)
    }

    @Test
    fun `Telegram user that already has a phone gets the code without being asked`() {
        userRepository.save(User(email = "").also {
            it.linkProvider(AuthProvider.TELEGRAM, telegramUserId.toString())
            it.telegramUserId = telegramUserId
            it.phone = phoneE164
        })
        val getCode = captureCode()
        val sessionId = initSession()

        start(sessionId)

        verify(telegramBotService, never()).requestContact(anyLong(), anyString(), anyString())
        verifyCode(sessionId, getCode()).andExpect(status().isOk)
    }
}
