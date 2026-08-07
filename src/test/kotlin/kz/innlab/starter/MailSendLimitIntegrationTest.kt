package kz.innlab.starter

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.notification.repository.MailHistoryRepository
import kz.innlab.starter.notification.service.PushService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Verifies the abuse controls on the authenticated mail-send endpoints:
 * per-user hourly quota and attachment size/count caps.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MailSendLimitIntegrationTest {

    companion object {
        private val greenMail = GreenMail(ServerSetupTest.SMTP)

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
            registry.add("app.mail.smtp.host") { "localhost" }
            registry.add("app.mail.smtp.port") { greenMail.smtp.port }
            registry.add("app.mail.smtp.from") { "test@example.com" }
            registry.add("app.mail.smtp.ssl-enabled") { "false" }
            registry.add("app.mail.retry.max-attempts") { "1" }
            registry.add("app.mail.retry.delay-ms") { "50" }
            registry.add("app.mail.limits.per-user-per-hour") { "2" }
            registry.add("app.mail.limits.max-attachments") { "2" }
            registry.add("app.mail.limits.max-attachment-bytes") { "64" }
            registry.add("app.mail.limits.max-total-attachment-bytes") { "96" }
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
    private lateinit var tokenService: TokenService

    private lateinit var accessToken: String

    @BeforeEach
    fun setUp() {
        mailHistoryRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()

        val user = userRepository.save(
            User(email = "sender@example.com").also { it.providers.add(AuthProvider.LOCAL) }
        )
        accessToken = tokenService.generateAccessToken(user.id, setOf(Role.USER))
    }

    private fun sendMail() = mockMvc.perform(
        post("/api/v1/mail/send")
            .header("Authorization", "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"to": "recipient@example.com", "subject": "S", "textBody": "B"}""")
    )

    @Test
    fun `send is rejected with 409 once the hourly quota is exhausted`() {
        // per-user-per-hour = 2
        sendMail().andExpect(status().isAccepted)
        sendMail().andExpect(status().isAccepted)
        sendMail().andExpect(status().isConflict)
    }

    @Test
    fun `attachment larger than the cap is rejected with 400`() {
        val oversized = MockMultipartFile(
            "files", "big.txt", "text/plain", ByteArray(128) { 'a'.code.toByte() }
        )
        val email = MockMultipartFile(
            "email", "", MediaType.APPLICATION_JSON_VALUE,
            """{"to": "recipient@example.com", "subject": "S", "textBody": "B"}""".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/v1/mail/send/with-attachments")
                .file(email)
                .file(oversized)
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `too many attachments are rejected with 400`() {
        val email = MockMultipartFile(
            "email", "", MediaType.APPLICATION_JSON_VALUE,
            """{"to": "recipient@example.com", "subject": "S", "textBody": "B"}""".toByteArray()
        )
        val small = { name: String ->
            MockMultipartFile("files", name, "text/plain", ByteArray(8) { 'a'.code.toByte() })
        }

        mockMvc.perform(
            multipart("/api/v1/mail/send/with-attachments")
                .file(email)
                .file(small("a.txt"))
                .file(small("b.txt"))
                .file(small("c.txt"))
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isBadRequest)
    }
}
