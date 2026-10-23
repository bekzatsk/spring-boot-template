package kz.innlab.starter

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.notification.service.PushService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

/**
 * A code email must reach a real SMTP server. The rest of the suite mocks EmailService, which hid
 * that the starter's own mail services never sent a code: the send was deferred twice "after
 * commit", and the second deferral was registered too late to run.
 *
 * Deliberately not @Transactional: the bug lives in the commit of the request's own transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailCodeDeliveryIntegrationTest {

    companion object {
        private val greenMail = GreenMail(ServerSetupTest.SMTP.dynamicPort())

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
            registry.add("app.mail.retry.max-attempts") { "1" }
        }
    }

    @MockitoBean
    private lateinit var pushService: PushService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @BeforeEach
    fun setUp() {
        greenMail.purgeEmailFromAllMailboxes()
        verificationCodeRepository.deleteAll()
    }

    @Test
    fun `email login code is delivered over SMTP with readable copy`() {
        mockMvc.perform(
            post("/api/v1/auth/email/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"deliver@example.com"}""")
        ).andExpect(status().isOk)

        await().atMost(Duration.ofSeconds(10)).until { greenMail.receivedMessages.isNotEmpty() }

        val message = greenMail.receivedMessages.single()
        val body = GreenMailUtil.getBody(message)
        assert(message.allRecipients.single().toString() == "deliver@example.com")
        assert(Regex("\\b\\d{6}\\b").containsMatchIn(body)) { "no code in body: $body" }
        // The purpose enum name is an internal identifier, not copy for the recipient.
        assert(!message.subject.contains("EMAIL_LOGIN")) { "subject: ${message.subject}" }
        assert(!body.contains("EMAIL_LOGIN")) { "body: $body" }
    }
}
