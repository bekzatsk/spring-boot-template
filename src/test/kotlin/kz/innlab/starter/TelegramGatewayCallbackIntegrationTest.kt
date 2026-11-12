package kz.innlab.starter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sun.net.httpserver.HttpServer
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A stand-in for gatewayapi.telegram.org: answers sendVerificationMessage with a fresh request_id
 * and remembers which code went to which request.
 */
object GatewayStub {
    private val counter = AtomicInteger()
    /** request_id → code, in send order. */
    val sent: MutableMap<String, String> = Collections.synchronizedMap(LinkedHashMap())
    private val mapper = JsonMapper.builder().build()

    val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/sendVerificationMessage") { exchange ->
                val request = mapper.readTree(exchange.requestBody.readAllBytes())
                val requestId = "req-${counter.incrementAndGet()}"
                sent[requestId] = request.get("code").asString()
                val phone = request.get("phone_number").asString().removePrefix("+")
                val answer = """{"ok":true,"result":{"request_id":"$requestId","phone_number":"$phone","request_cost":0.01}}"""
                    .toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, answer.size.toLong())
                exchange.responseBody.use { it.write(answer) }
            }
            start()
        }
    }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("app.auth.telegram-gateway.base-url") { "http://127.0.0.1:${server.address.port}" }
    }
}

/** Shared flow: phone login over the Gateway, then a signed delivery report. */
abstract class TelegramGatewayCallbackTestBase {

    @MockitoBean protected lateinit var smsService: SmsService
    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var verificationCodeRepository: VerificationCodeRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var userRepository: UserRepository

    protected val token = "gw-test-token"

    /** Codes that went out by SMS, in order. */
    protected val smsCodes: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        verificationCodeRepository.deleteAll()
        userRepository.deleteAll()
        smsCodes.clear()
        doAnswer { smsCodes += it.arguments[1] as String; null }
            .`when`(smsService).sendCode(anyString(), anyString())
    }

    /** Requests a phone code; returns the verification id and the Gateway request that carried it. */
    protected fun requestCode(phone: String): Pair<String, String> {
        val response = mockMvc.perform(
            post("/api/v1/auth/phone/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "$phone"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val verificationId = JsonMapper.builder().build().readTree(response).get("verificationId").asString()
        val requestId = verificationCodeRepository.findById(UUID.fromString(verificationId)).orElseThrow().gatewayRequestId
        assertThat(requestId).describedAs("request_id stored on the verification").isNotNull()
        return verificationId to requestId!!
    }

    protected fun report(requestId: String, phone: String, delivery: String): ResultActions {
        val body = """{"request_id":"$requestId","phone_number":"${phone.removePrefix("+")}","request_cost":0.01,""" +
            """"delivery_status":{"status":"$delivery","updated_at":${Instant.now().epochSecond}}}"""
        val timestamp = Instant.now().epochSecond.toString()
        val key = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val signature = mac.doFinal("$timestamp\n$body".toByteArray()).joinToString("") { "%02x".format(it) }
        return mockMvc.perform(
            post("/api/v1/auth/telegram-gateway/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Timestamp", timestamp)
                .header("X-Request-Signature", signature)
                .content(body)
        )
    }

    protected fun verify(verificationId: String, phone: String, code: String): ResultActions = mockMvc.perform(
        post("/api/v1/auth/phone/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"verificationId": "$verificationId", "phone": "$phone", "code": "$code"}""")
    )
}

@SpringBootTest(
    properties = [
        "app.auth.telegram-gateway.enabled=true",
        "app.auth.telegram-gateway.token=gw-test-token",
        "app.auth.telegram-gateway.ttl-seconds=60",
        "app.auth.telegram-gateway.callback-url=https://api.example.test/api/v1/auth/telegram-gateway/callback",
        "app.auth.telegram-gateway.fallback-on-undelivered=true",
        "app.auth.otp.delivery-order=telegram,sms"
    ]
)
@AutoConfigureMockMvc
class TelegramGatewayCallbackIntegrationTest : TelegramGatewayCallbackTestBase() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun gateway(registry: DynamicPropertyRegistry) = GatewayStub.register(registry)
    }

    @MockitoSpyBean private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `a verification of the old code that started before the replacement is refused`() {
        val phone = "+77011230006"
        val (verificationId, requestId) = requestCode(phone)
        val oldCode = GatewayStub.sent.getValue(requestId)
        val oldHash = verificationCodeRepository.findById(UUID.fromString(verificationId)).orElseThrow().codeHash

        // The verification has read the record and is checking the old code when the expired
        // report replaces it — in another thread, committed before the check returns.
        doAnswer { invocation ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                pool.submit { report(requestId, phone, "expired").andExpect(status().isOk) }.get(15, TimeUnit.SECONDS)
            } finally {
                pool.shutdownNow()
            }
            invocation.callRealMethod()
        }.`when`(passwordEncoder).matches(oldCode, oldHash)

        verify(verificationId, phone, oldCode).andExpect(status().isUnauthorized)

        assertThat(smsCodes).describedAs("the replacement happened").hasSize(1)
        verify(verificationId, phone, smsCodes.single()).andExpect(status().isOk)
    }

    @Test
    fun `an expired delivery sends a new code by the next channel and the old code stops working`() {
        val phone = "+77011230001"
        val (verificationId, requestId) = requestCode(phone)
        val oldCode = GatewayStub.sent.getValue(requestId)
        assertThat(smsCodes).describedAs("telegram accepted the code, sms not tried yet").isEmpty()

        val logger = LoggerFactory.getLogger("kz.innlab.starter") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previousLevel = logger.level
        logger.addAppender(appender)
        logger.level = Level.DEBUG
        try {
            report(requestId, phone, "expired").andExpect(status().isOk)
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }

        assertThat(smsCodes).hasSize(1)
        val newCode = smsCodes.single()
        assertThat(newCode).isNotEqualTo(oldCode)

        val logged = appender.list.map { it.formattedMessage }
        assertThat(logged).describedAs("the report is logged").anyMatch { it.contains(requestId) }
        assertThat(logged).noneMatch {
            it.contains(oldCode) || it.contains(newCode) || it.contains(phone.removePrefix("+"))
        }

        // Same verification id: the client keeps the one it got.
        verify(verificationId, phone, oldCode).andExpect(status().isUnauthorized)
        verify(verificationId, phone, newCode).andExpect(status().isOk)
    }

    @Test
    fun `a repeated expired report does not issue a second code`() {
        val phone = "+77011230002"
        val (_, requestId) = requestCode(phone)

        report(requestId, phone, "expired").andExpect(status().isOk)
        report(requestId, phone, "expired").andExpect(status().isOk)

        assertThat(smsCodes).hasSize(1)
    }

    @Test
    fun `a delivered report changes nothing`() {
        val phone = "+77011230003"
        val (verificationId, requestId) = requestCode(phone)

        report(requestId, phone, "delivered").andExpect(status().isOk)

        verify(smsService, never()).sendCode(anyString(), anyString())
        verify(verificationId, phone, GatewayStub.sent.getValue(requestId)).andExpect(status().isOk)
    }

    @Test
    fun `an unknown request_id gets 200 and does nothing`() {
        val phone = "+77011230004"
        val (verificationId, requestId) = requestCode(phone)

        report("req-unknown", phone, "expired").andExpect(status().isOk)

        verify(smsService, never()).sendCode(anyString(), anyString())
        verify(verificationId, phone, GatewayStub.sent.getValue(requestId)).andExpect(status().isOk)
    }

    @Test
    fun `an expired report for a used code sends nothing`() {
        val phone = "+77011230005"
        val (verificationId, requestId) = requestCode(phone)
        verify(verificationId, phone, GatewayStub.sent.getValue(requestId)).andExpect(status().isOk)

        report(requestId, phone, "expired").andExpect(status().isOk)

        verify(smsService, never()).sendCode(anyString(), anyString())
    }

    @Test
    fun `the endpoint is public but refuses a bad signature`() {
        mockMvc.perform(
            post("/api/v1/auth/telegram-gateway/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Timestamp", Instant.now().epochSecond.toString())
                .header("X-Request-Signature", "00".repeat(32))
                .content("""{"request_id":"req-x","delivery_status":{"status":"expired"}}""")
        ).andExpect(status().isUnauthorized)
        verify(smsService, never()).sendCode(anyString(), anyString())
    }
}

@SpringBootTest(
    properties = [
        "app.auth.telegram-gateway.enabled=true",
        "app.auth.telegram-gateway.token=gw-test-token",
        "app.auth.otp.delivery-order=telegram,sms"
    ]
)
@AutoConfigureMockMvc
class TelegramGatewayCallbackFallbackOffIntegrationTest : TelegramGatewayCallbackTestBase() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun gateway(registry: DynamicPropertyRegistry) = GatewayStub.register(registry)
    }

    @Test
    fun `with fallback off an expired report sends nothing and the code stays valid`() {
        val phone = "+77011230011"
        val (verificationId, requestId) = requestCode(phone)

        report(requestId, phone, "expired").andExpect(status().isOk)

        verify(smsService, never()).sendCode(anyString(), anyString())
        verify(verificationId, phone, GatewayStub.sent.getValue(requestId)).andExpect(status().isOk)
    }
}

/** With a fixed dev code the generator would hand out the very code that was not delivered. */
@SpringBootTest(
    properties = [
        "app.auth.telegram-gateway.enabled=true",
        "app.auth.telegram-gateway.token=gw-test-token",
        "app.auth.telegram-gateway.ttl-seconds=60",
        "app.auth.telegram-gateway.fallback-on-undelivered=true",
        "app.auth.otp.delivery-order=telegram,sms",
        "app.auth.sms.dev-code=123456"
    ]
)
@AutoConfigureMockMvc
class TelegramGatewayCallbackDevCodeIntegrationTest : TelegramGatewayCallbackTestBase() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun gateway(registry: DynamicPropertyRegistry) = GatewayStub.register(registry)
    }

    @Test
    fun `the reissued code differs from the undelivered one even with a dev code`() {
        val phone = "+77011230021"
        val (verificationId, requestId) = requestCode(phone)
        assertThat(GatewayStub.sent.getValue(requestId)).isEqualTo("123456")

        report(requestId, phone, "expired").andExpect(status().isOk)

        assertThat(smsCodes).hasSize(1)
        assertThat(smsCodes.single()).isNotEqualTo("123456")
        verify(verificationId, phone, "123456").andExpect(status().isUnauthorized)
        verify(verificationId, phone, smsCodes.single()).andExpect(status().isOk)
    }
}
