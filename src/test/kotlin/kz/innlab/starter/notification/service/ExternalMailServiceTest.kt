package kz.innlab.starter.notification.service

import com.sun.net.httpserver.HttpServer
import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.notification.model.MailHistory
import kz.innlab.starter.notification.model.MailStatus
import kz.innlab.starter.notification.repository.MailHistoryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class ExternalMailServiceTest {

    private val requests = CopyOnWriteArrayList<String>()
    private var answerDelay: Duration = Duration.ZERO

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/send") { exchange ->
            requests += exchange.requestBody.readAllBytes().decodeToString()
            Thread.sleep(answerDelay.toMillis())
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        start()
    }

    private val historyRepository = mock(MailHistoryRepository::class.java).also {
        `when`(it.save(any(MailHistory::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
    }

    private fun service(readTimeout: Duration = Duration.ofSeconds(5)) = ExternalMailService(
        MailProperties(
            external = MailProperties.ExternalProperties(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                masterKey = "key",
                readTimeout = readTimeout
            )
        ),
        historyRepository
    )

    @AfterEach
    fun stopServer() = server.stop(0)

    @Test
    fun `code email carries readable copy and the code`() {
        service().sendCode("user@example.com", "123456", "EMAIL_LOGIN")

        val body = requests.single()
        assertTrue(body.contains("123456"), body)
        assertTrue(body.contains("Your sign-in code"), body)
        assertTrue(body.contains("\"isHtml\":true"), body)
        assertFalse(body.contains("EMAIL_LOGIN"), body)
    }

    @Test
    fun `a late answer is an unknown outcome, not a failure`() {
        answerDelay = Duration.ofMillis(1500)
        val service = service(readTimeout = Duration.ofMillis(300))

        assertThrows<MailOutcomeUnknownException> {
            service.send("user@example.com", "Subject", "text", null)
        }

        val id = service.sendEmail(UUID.randomUUID(), "user@example.com", "Subject", "text", null)
        val saved = org.mockito.ArgumentCaptor.forClass(MailHistory::class.java)
        org.mockito.Mockito.verify(historyRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture())
        val history = saved.allValues.last { it.id == id }
        assertEquals(MailStatus.PENDING, history.status)
    }

    @Test
    fun `an unreachable service is a failure`() {
        val service = service()
        server.stop(0)

        val id = service.sendEmail(UUID.randomUUID(), "user@example.com", "Subject", "text", null)
        val saved = org.mockito.ArgumentCaptor.forClass(MailHistory::class.java)
        org.mockito.Mockito.verify(historyRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture())
        assertEquals(MailStatus.FAILED, saved.allValues.last { it.id == id }.status)
    }
}
