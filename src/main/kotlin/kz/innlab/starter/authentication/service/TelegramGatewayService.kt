package kz.innlab.starter.authentication.service

import kz.innlab.starter.config.TelegramGatewayProperties
import kz.innlab.starter.shared.util.maskPhone
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/** The Gateway did not accept the message. The message never carries the code or the phone number. */
class TelegramGatewayException(message: String) : RuntimeException(message)

/**
 * Phone OTP delivery through the Telegram Gateway API — channel `telegram` in
 * `app.auth.otp.delivery-order`.
 *
 * Calls `POST {base-url}/sendVerificationMessage` with `Authorization: Bearer <token>` and the
 * starter's own `code`: VerificationCodeService generates and checks it, Telegram only delivers.
 * So `code_length`, `checkVerificationStatus` and `revokeVerificationMessage` are not used.
 *
 * `checkSendAbility` is not called either: it is billed like a send, and a number that cannot
 * receive a Gateway message makes `sendVerificationMessage` itself answer `ok=false` — which,
 * like any HTTP error or transport failure, is thrown as [TelegramGatewayException] so that
 * OtpDeliveryService moves on to the next channel.
 *
 * `ok=true` means Telegram accepted the message (a RequestStatus comes back), not that it was
 * delivered or read: the DeliveryStatus arrives later, at `callback-url`, and nothing falls back
 * on it. Delivery reports are not processed by the starter yet.
 *
 * Called by OtpDeliveryService after the transaction commits, never inside it.
 */
@Service
@ConditionalOnProperty(name = ["app.auth.telegram-gateway.enabled"], havingValue = "true")
class TelegramGatewayService(
    private val properties: TelegramGatewayProperties,
    private val restClient: RestClient = defaultRestClient(properties)
) : OtpDeliveryChannel {

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramGatewayService::class.java)

        const val CHANNEL_NAME = "telegram"

        /** Plain JDK HTTP with the configured timeouts: a hung Gateway must not hold a request thread. */
        fun defaultRestClient(properties: TelegramGatewayProperties): RestClient {
            val requestFactory = SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeout)
                setReadTimeout(properties.readTimeout)
            }
            return RestClient.builder()
                .baseUrl(properties.baseUrl)
                .requestFactory(requestFactory)
                .build()
        }
    }

    override val name: String = CHANNEL_NAME

    override fun sendCode(phone: String, code: String) {
        if (properties.token.isBlank()) {
            throw TelegramGatewayException(
                "Telegram Gateway is enabled but app.auth.telegram-gateway.token is not set"
            )
        }

        val response = try {
            restClient.post()
                .uri("/sendVerificationMessage")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(phone, code))
                .retrieve()
                .body(Map::class.java)
        } catch (ex: RestClientResponseException) {
            throw TelegramGatewayException(
                "Telegram Gateway answered HTTP ${ex.statusCode.value()}" + (gatewayError(ex)?.let { ": $it" } ?: "")
            )
        } catch (ex: RestClientException) {
            throw TelegramGatewayException("Telegram Gateway request failed: ${ex.javaClass.simpleName}")
        }

        if (response?.get("ok") != true) {
            throw TelegramGatewayException(
                "Telegram Gateway refused the message: ${response?.get("error") ?: "no error given"}"
            )
        }
        val requestId = (response["result"] as? Map<*, *>)?.get("request_id")
        logger.debug("[TELEGRAM-GATEWAY] verification message accepted for {}: request_id={}", maskPhone(phone), requestId)
    }

    private fun requestBody(phone: String, code: String): Map<String, Any> = buildMap {
        put("phone_number", phone)
        put("code", code)
        put("ttl", properties.ttlSeconds)
        if (properties.senderUsername.isNotBlank()) put("sender_username", properties.senderUsername)
        if (properties.callbackUrl.isNotBlank()) put("callback_url", properties.callbackUrl)
    }

    /** The Gateway's `error` string from a non-2xx JSON answer, if there is one. */
    private fun gatewayError(ex: RestClientResponseException): Any? =
        runCatching { ex.getResponseBodyAs(Map::class.java)?.get("error") }.getOrNull()
}
