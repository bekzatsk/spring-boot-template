package kz.innlab.starter.authentication.controller

import kz.innlab.starter.authentication.dto.TelegramGatewayRequestStatus
import kz.innlab.starter.authentication.service.TelegramGatewayReportService
import kz.innlab.starter.config.TelegramGatewayProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The signature check of the delivery-report endpoint, against the contract in the Gateway docs:
 * hex(HMAC-SHA256(key = SHA256(token), data = timestamp + "\n" + raw body)).
 */
class TelegramGatewayCallbackControllerTest {

    private val token = "gw-test-token"
    private val body = """{"request_id":"req-1","phone_number":"77011234567","request_cost":0.01,""" +
        """"delivery_status":{"status":"delivered","updated_at":1700000000}}"""

    private val reportService: TelegramGatewayReportService = mock(TelegramGatewayReportService::class.java)

    private fun controller(token: String = this.token) = TelegramGatewayCallbackController(
        TelegramGatewayProperties(enabled = true, token = token),
        reportService,
        JsonMapper.builder().build()
    )

    /** Computed here from the documented formula, not by the code under test. */
    private fun signature(timestamp: String, raw: ByteArray, token: String = this.token): String {
        val key = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        return mac.doFinal("$timestamp\n".toByteArray() + raw).joinToString("") { "%02x".format(it) }
    }

    private val now get() = Instant.now().epochSecond.toString()

    @Test
    fun `a validly signed report is accepted and handed on`() {
        val ts = now
        val response = controller().receive(body.toByteArray(), ts, signature(ts, body.toByteArray()))
        assertThat(response.statusCode.value()).isEqualTo(200)
        verify(reportService).handle(any(TelegramGatewayRequestStatus::class.java) ?: TelegramGatewayRequestStatus())
    }

    @Test
    fun `a signature made with the token itself instead of its hash is refused`() {
        val ts = now
        val key = token.toByteArray()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val wrong = mac.doFinal("$ts\n".toByteArray() + body.toByteArray()).joinToString("") { "%02x".format(it) }

        assertThat(controller().receive(body.toByteArray(), ts, wrong).statusCode.value()).isEqualTo(401)
        verifyNotHandled()
    }

    @Test
    fun `a body that differs by one byte from the signed one is refused`() {
        val ts = now
        val signed = body.toByteArray()
        val tampered = signed.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }

        assertThat(controller().receive(tampered, ts, signature(ts, signed)).statusCode.value()).isEqualTo(401)
        verifyNotHandled()
    }

    @Test
    fun `missing signature headers are refused`() {
        val ts = now
        val sig = signature(ts, body.toByteArray())
        assertThat(controller().receive(body.toByteArray(), null, sig).statusCode.value()).isEqualTo(401)
        assertThat(controller().receive(body.toByteArray(), ts, null).statusCode.value()).isEqualTo(401)
        verifyNotHandled()
    }

    @Test
    fun `a correctly signed report older than the window is refused`() {
        val old = (Instant.now().epochSecond - 6 * 60).toString()
        assertThat(controller().receive(body.toByteArray(), old, signature(old, body.toByteArray())).statusCode.value())
            .isEqualTo(401)
        verifyNotHandled()
    }

    @Test
    fun `with no token configured nothing is accepted`() {
        val ts = now
        // Signed with the empty token — what an attacker could compute when none is set.
        val response = controller(token = "").receive(body.toByteArray(), ts, signature(ts, body.toByteArray(), token = ""))
        assertThat(response.statusCode.is2xxSuccessful).isFalse()
        verifyNotHandled()
    }

    @Test
    fun `a signed but malformed report still gets 200, so the Gateway does not retry it`() {
        val ts = now
        val raw = "not json".toByteArray()
        assertThat(controller().receive(raw, ts, signature(ts, raw)).statusCode.value()).isEqualTo(200)
        verifyNotHandled()
    }

    private fun verifyNotHandled() =
        verify(reportService, never()).handle(any(TelegramGatewayRequestStatus::class.java) ?: TelegramGatewayRequestStatus())
}
