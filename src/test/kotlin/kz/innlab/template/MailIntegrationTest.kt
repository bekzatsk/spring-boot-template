package kz.innlab.template

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import kz.innlab.template.authentication.repository.RefreshTokenRepository
import kz.innlab.template.authentication.service.TokenService
import kz.innlab.template.notification.repository.MailHistoryRepository
import kz.innlab.template.notification.repository.NotificationPreferenceRepository
import kz.innlab.template.notification.service.PushService
import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.Role
import kz.innlab.template.user.model.User
import kz.innlab.template.user.repository.UserRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
class MailIntegrationTest {

    companion object {
        private val greenMail = GreenMail(ServerSetupTest.SMTP_IMAP)

        @JvmStatic
        @BeforeAll
        fun startMailServer() {
            greenMail.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMailServer() {
            greenMail.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureMailProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.mail.enabled") { "true" }
            registry.add("spring.mail.host") { "localhost" }
            registry.add("spring.mail.port") { greenMail.smtp.port }
            registry.add("spring.mail.properties.mail.smtp.auth") { "false" }
            registry.add("spring.mail.properties.mail.smtp.starttls.enable") { "false" }
            registry.add("app.mail.smtp.host") { "localhost" }
            registry.add("app.mail.smtp.port") { greenMail.smtp.port }
            registry.add("app.mail.smtp.username") { "" }
            registry.add("app.mail.smtp.password") { "" }
            registry.add("app.mail.smtp.from") { "test@example.com" }
            registry.add("app.mail.smtp.ssl-enabled") { "false" }
            registry.add("app.mail.imap.host") { "localhost" }
            registry.add("app.mail.imap.port") { greenMail.imap.port }
            registry.add("app.mail.imap.username") { "inbox@example.com" }
            registry.add("app.mail.imap.password") { "password" }
            registry.add("app.mail.imap.ssl-enabled") { "false" }
            registry.add("app.mail.retry.max-attempts") { "1" }
            registry.add("app.mail.retry.delay-ms") { "100" }
        }
    }

    @MockitoBean
    private lateinit var pushService: PushService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var mailHistoryRepository: MailHistoryRepository

    @Autowired
    private lateinit var notificationPreferenceRepository: NotificationPreferenceRepository

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var accessToken: String
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        mailHistoryRepository.deleteAll()
        notificationPreferenceRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()

        testUser = userRepository.save(
            User(email = "sender@example.com").also {
                it.providers.add(AuthProvider.LOCAL)
            }
        )
        accessToken = tokenService.generateAccessToken(testUser.id, setOf(Role.USER))

        greenMail.reset()
        greenMail.setUser("inbox@example.com", "inbox@example.com", "password")
        greenMail.setUser("test@example.com", "test@example.com", "password")
    }

    private fun authHeader() = "Bearer $accessToken"

    // --- Send email ---

    @Test
    fun `sendEmail returns 202 with mailId`() {
        mockMvc.perform(
            post("/api/v1/mail/send")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"to": "recipient@example.com", "subject": "Test Subject", "textBody": "Hello"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.mailId").exists())
    }

    @Test
    fun `sendEmail delivers to SMTP server`() {
        mockMvc.perform(
            post("/api/v1/mail/send")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"to": "recipient@example.com", "subject": "Delivery Test", "textBody": "Hello from test"}""")
        )
            .andExpect(status().isAccepted)

        await().atMost(Duration.ofSeconds(5)).until { greenMail.receivedMessages.isNotEmpty() }

        val messages = greenMail.receivedMessages
        assert(messages.isNotEmpty()) { "Expected at least one received message" }
        assert(messages[0].subject == "Delivery Test")
    }

    @Test
    fun `sendEmail with HTML body`() {
        mockMvc.perform(
            post("/api/v1/mail/send")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"to": "recipient@example.com", "subject": "HTML Test", "htmlBody": "<h1>Hello</h1>"}""")
        )
            .andExpect(status().isAccepted)

        await().atMost(Duration.ofSeconds(5)).until { greenMail.receivedMessages.isNotEmpty() }

        val messages = greenMail.receivedMessages
        assert(messages.isNotEmpty()) { "Expected at least one received message" }
        assert(messages[0].subject == "HTML Test")
    }

    @Test
    fun `mailHistory tracks sent email`() {
        mockMvc.perform(
            post("/api/v1/mail/send")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"to": "recipient@example.com", "subject": "History Test", "textBody": "Track this"}""")
        )
            .andExpect(status().isAccepted)

        await().atMost(Duration.ofSeconds(5)).until { greenMail.receivedMessages.isNotEmpty() }

        mockMvc.perform(
            get("/api/v1/mail/history")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].subject").value("History Test"))
            .andExpect(jsonPath("$[0].toAddress").value("recipient@example.com"))
    }

    // --- IMAP inbox ---

    @Test
    fun `listInbox returns messages`() {
        GreenMailUtil.sendTextEmailTest("inbox@example.com", "sender@test.com", "Inbox Test", "Message body")

        mockMvc.perform(
            get("/api/v1/mail/inbox")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.messages[0].subject").value("Inbox Test"))
    }

    @Test
    fun `getMessage returns full email`() {
        GreenMailUtil.sendTextEmailTest("inbox@example.com", "sender@test.com", "Full Email", "Body content")

        mockMvc.perform(
            get("/api/v1/mail/inbox/1")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subject").value("Full Email"))
            .andExpect(jsonPath("$.textBody").value("Body content"))
    }

    // --- Authentication ---

    @Test
    fun `unauthenticated request to mail returns 401`() {
        mockMvc.perform(get("/api/v1/mail/inbox"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `unauthenticated request to send returns 401`() {
        mockMvc.perform(
            post("/api/v1/mail/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"to": "a@b.com", "subject": "X", "textBody": "Y"}""")
        )
            .andExpect(status().isUnauthorized)
    }
}
