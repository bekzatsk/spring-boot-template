package kz.innlab.starter.authentication.service

import kz.innlab.starter.config.TelegramGatewayProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.hasKey

/**
 * The Gateway client against a stubbed HTTP server: what goes on the wire, and that every way the
 * Gateway can refuse a message surfaces as an exception — which is what moves OtpDeliveryService
 * on to the next channel.
 */
class TelegramGatewayServiceTest {

    private val phone = "+77011234567"
    private val code = "482915"

    private val okResponse = """
        {"ok":true,"result":{"request_id":"req-1","phone_number":"77011234567","request_cost":0.01}}
    """.trimIndent()

    private fun gateway(properties: TelegramGatewayProperties): Pair<TelegramGatewayService, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        return TelegramGatewayService(properties, builder.build()) to server
    }

    private fun props(
        token: String = "gw-test-token",
        senderUsername: String = "",
        callbackUrl: String = "",
        ttlSeconds: Int = 300
    ) = TelegramGatewayProperties(
        enabled = true,
        token = token,
        senderUsername = senderUsername,
        callbackUrl = callbackUrl,
        ttlSeconds = ttlSeconds
    )

    @Test
    fun `posts our own code to sendVerificationMessage with a bearer token`() {
        val (service, server) = gateway(props(ttlSeconds = 120))
        server.expect(requestTo("https://gatewayapi.telegram.org/sendVerificationMessage"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer gw-test-token"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.phone_number").value(phone))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.ttl").value(120))
            // optional parameters are left out when not configured, not sent blank
            .andExpect(jsonPath("$", not(hasKey("sender_username"))))
            .andExpect(jsonPath("$", not(hasKey("callback_url"))))
            .andExpect(jsonPath("$", not(hasKey("code_length"))))
            .andRespond(withSuccess(okResponse, MediaType.APPLICATION_JSON))

        service.sendCode(phone, code)

        server.verify()
    }

    @Test
    fun `passes sender username and callback url when configured`() {
        val (service, server) = gateway(
            props(senderUsername = "acme_codes", callbackUrl = "https://api.example.test/telegram/report")
        )
        server.expect(requestTo("https://gatewayapi.telegram.org/sendVerificationMessage"))
            .andExpect(jsonPath("$.sender_username").value("acme_codes"))
            .andExpect(jsonPath("$.callback_url").value("https://api.example.test/telegram/report"))
            .andRespond(withSuccess(okResponse, MediaType.APPLICATION_JSON))

        service.sendCode(phone, code)

        server.verify()
    }

    @Test
    fun `ok false is an exception carrying the gateway error`() {
        val (service, server) = gateway(props())
        server.expect(requestTo("https://gatewayapi.telegram.org/sendVerificationMessage"))
            .andRespond(withSuccess("""{"ok":false,"error":"PHONE_NUMBER_INVALID"}""", MediaType.APPLICATION_JSON))

        val ex = assertThrows<TelegramGatewayException> { service.sendCode(phone, code) }

        assertThat(ex.message).contains("PHONE_NUMBER_INVALID")
        assertThat(ex.message).doesNotContain(code).doesNotContain(phone)
        server.verify()
    }

    @Test
    fun `an HTTP error is an exception`() {
        val (service, server) = gateway(props())
        server.expect(requestTo("https://gatewayapi.telegram.org/sendVerificationMessage"))
            .andRespond(
                withStatus(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"ok":false,"error":"ACCESS_TOKEN_INVALID"}""")
            )

        val ex = assertThrows<TelegramGatewayException> { service.sendCode(phone, code) }

        assertThat(ex.message).contains("401")
        server.verify()
    }

    @Test
    fun `a 5xx without a JSON body is an exception`() {
        val (service, server) = gateway(props())
        server.expect(requestTo("https://gatewayapi.telegram.org/sendVerificationMessage"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        assertThrows<TelegramGatewayException> { service.sendCode(phone, code) }
        server.verify()
    }

    @Test
    fun `a blank token fails without calling the gateway`() {
        val (service, server) = gateway(props(token = ""))

        val ex = assertThrows<TelegramGatewayException> { service.sendCode(phone, code) }

        assertThat(ex.message).contains("app.auth.telegram-gateway.token")
        server.verify() // no request expected, none made
    }

    @Test
    fun `the channel is named telegram for delivery-order`() {
        assertThat(TelegramGatewayService(props()).name).isEqualTo("telegram")
    }

    @Test
    fun `stores the request_id on the verification the code belongs to`() {
        val recorder = org.mockito.Mockito.mock(VerificationAttemptRecorder::class.java)
        val properties = props()
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        val service = TelegramGatewayService(properties, builder.build(), recorder)
        val verificationId = java.util.UUID.randomUUID()
        server.expect(requestTo("https://gatewayapi.telegram.org/sendVerificationMessage"))
            .andRespond(withSuccess(okResponse, MediaType.APPLICATION_JSON))

        service.sendCode(phone, code, verificationId)

        org.mockito.Mockito.verify(recorder).recordGatewayRequest(verificationId, "req-1")
    }

    @Test
    fun `failing to store the request_id does not fail the send`() {
        // Throwing would make OtpDeliveryService send the code again through the next channel.
        val recorder = org.mockito.Mockito.mock(VerificationAttemptRecorder::class.java)
        val verificationId = java.util.UUID.randomUUID()
        org.mockito.Mockito.doThrow(IllegalStateException("db down"))
            .`when`(recorder).recordGatewayRequest(verificationId, "req-1")
        val properties = props()
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://gatewayapi.telegram.org/sendVerificationMessage"))
            .andRespond(withSuccess(okResponse, MediaType.APPLICATION_JSON))

        TelegramGatewayService(properties, builder.build(), recorder).sendCode(phone, code, verificationId)

        server.verify()
    }
}
