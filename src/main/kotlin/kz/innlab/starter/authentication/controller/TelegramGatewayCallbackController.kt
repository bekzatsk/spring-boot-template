package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Hidden
import kz.innlab.starter.authentication.dto.TelegramGatewayRequestStatus
import kz.innlab.starter.authentication.service.TelegramGatewayReportService
import kz.innlab.starter.config.TelegramGatewayProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Receives Telegram Gateway delivery reports (`callback-url`): `POST` of a `RequestStatus` JSON.
 *
 * Public (no JWT, no CSRF), so every report must prove it comes from the Gateway:
 * `X-Request-Signature` = hex(HMAC-SHA256(key = SHA256(api token),
 * data = `X-Request-Timestamp` + "\n" + raw body)). The HMAC runs over the bytes as received —
 * re-serialised JSON would not match — and is compared in constant time. A timestamp further than
 * `callback-max-age` from now is refused, so a captured report cannot be replayed.
 *
 * Fail closed: with no token configured nothing is accepted, as in TelegramWebhookController.
 * A refused report gets 401/403; the Gateway retries those and then drops the report.
 * A report with a valid signature always gets 200 — also when it is malformed, names an unknown
 * request or its processing fails — since a retry would not change the outcome.
 */
@RestController
@Hidden
@RequestMapping("/api/v1/auth/telegram-gateway")
@ConditionalOnProperty(name = ["app.auth.telegram-gateway.enabled"], havingValue = "true")
class TelegramGatewayCallbackController(
    private val properties: TelegramGatewayProperties,
    private val reportService: TelegramGatewayReportService,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramGatewayCallbackController::class.java)

        const val CALLBACK_PATH = "/api/v1/auth/telegram-gateway/callback"
        const val TIMESTAMP_HEADER = "X-Request-Timestamp"
        const val SIGNATURE_HEADER = "X-Request-Signature"
    }

    @PostMapping("/callback")
    fun receive(
        @RequestBody(required = false) body: ByteArray?,
        @RequestHeader(TIMESTAMP_HEADER, required = false) timestamp: String?,
        @RequestHeader(SIGNATURE_HEADER, required = false) signature: String?
    ): ResponseEntity<Void> {
        if (properties.token.isBlank()) {
            logger.error(
                "Telegram Gateway report rejected: app.auth.telegram-gateway.token is not set, so its signature " +
                    "cannot be checked"
            )
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (timestamp.isNullOrBlank() || signature.isNullOrBlank()) {
            logger.warn("Telegram Gateway report rejected: signature headers missing")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val sentAt = timestamp.trim().toLongOrNull()
        if (sentAt == null || abs(Instant.now().epochSecond - sentAt) > properties.callbackMaxAge.seconds) {
            logger.warn("Telegram Gateway report rejected: timestamp outside the {} window", properties.callbackMaxAge)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val raw = body ?: ByteArray(0)
        val expected = sign(timestamp, raw).toByteArray(Charsets.US_ASCII)
        val given = signature.trim().lowercase(Locale.ROOT).toByteArray(Charsets.US_ASCII)
        // Constant-time: a plain comparison returns at the first differing byte, which lets a
        // valid signature be recovered a byte at a time from response timing.
        if (!MessageDigest.isEqual(expected, given)) {
            logger.warn("Telegram Gateway report rejected: invalid signature")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val report = try {
            objectMapper.readValue(raw, TelegramGatewayRequestStatus::class.java)
        } catch (ex: Exception) {
            logger.warn("Telegram Gateway report with a valid signature could not be parsed: {}", ex.javaClass.name)
            return ResponseEntity.ok().build()
        }
        try {
            reportService.handle(report)
        } catch (ex: Exception) {
            // Class name only: a message may echo the report, number included.
            logger.error("Telegram Gateway report request_id={} failed: {}", report.requestId, ex.javaClass.name)
        }
        return ResponseEntity.ok().build()
    }

    private fun sign(timestamp: String, body: ByteArray): String {
        val secretKey = MessageDigest.getInstance("SHA-256").digest(properties.token.toByteArray(Charsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secretKey, "HmacSHA256")) }
        mac.update(timestamp.toByteArray(Charsets.UTF_8))
        mac.update('\n'.code.toByte())
        mac.update(body)
        return mac.doFinal().joinToString("") { "%02x".format(it) }
    }
}
