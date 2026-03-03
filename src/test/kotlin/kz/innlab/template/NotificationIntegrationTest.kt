package kz.innlab.template

import kz.innlab.template.authentication.repository.RefreshTokenRepository
import kz.innlab.template.authentication.service.TokenService
import kz.innlab.template.notification.model.NotificationHistory
import kz.innlab.template.notification.model.NotificationStatus
import kz.innlab.template.notification.model.NotificationTopic
import kz.innlab.template.notification.model.NotificationType
import kz.innlab.template.notification.repository.DeviceTokenRepository
import kz.innlab.template.notification.repository.NotificationHistoryRepository
import kz.innlab.template.notification.repository.NotificationTopicRepository
import kz.innlab.template.notification.service.PushService
import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.Role
import kz.innlab.template.user.model.User
import kz.innlab.template.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    @MockitoBean
    private lateinit var pushService: PushService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @Autowired
    private lateinit var notificationHistoryRepository: NotificationHistoryRepository

    @Autowired
    private lateinit var notificationTopicRepository: NotificationTopicRepository

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var accessToken: String
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        notificationHistoryRepository.deleteAll()
        deviceTokenRepository.deleteAll()
        notificationTopicRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()

        testUser = userRepository.save(
            User(email = "test@example.com").also {
                it.providers.add(AuthProvider.LOCAL)
            }
        )
        accessToken = tokenService.generateAccessToken(testUser.id, setOf(Role.USER))

        // Stub PushService methods
        `when`(pushService.sendToToken(anyString(), anyString(), anyString(), anyMap())).thenReturn("mock-message-id")
        `when`(pushService.sendMulticast(anyList(), anyString(), anyString(), anyMap())).thenReturn(emptyList())
        `when`(pushService.sendToTopic(anyString(), anyString(), anyString(), anyMap())).thenReturn("mock-message-id")
    }

    private fun authHeader() = "Bearer $accessToken"

    // --- Token Registration ---

    @Test
    fun `registerToken returns 201 with token details`() {
        mockMvc.perform(
            post("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"platform": "ANDROID", "fcmToken": "test-fcm-token-1", "deviceId": "device-1"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.platform").value("ANDROID"))
            .andExpect(jsonPath("$.deviceId").value("device-1"))
            .andExpect(jsonPath("$.fcmToken").value("test-fcm-token-1"))
    }

    @Test
    fun `registerToken upserts on same deviceId`() {
        // Register first token
        mockMvc.perform(
            post("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"platform": "ANDROID", "fcmToken": "old-token", "deviceId": "device-1"}""")
        )
            .andExpect(status().isCreated)

        // Register again with same deviceId but new fcmToken
        mockMvc.perform(
            post("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"platform": "ANDROID", "fcmToken": "new-token", "deviceId": "device-1"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.fcmToken").value("new-token"))

        // Should only have 1 token
        mockMvc.perform(
            get("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `registerToken max tokens exceeded returns 409`() {
        // Register 5 tokens (max-per-user=5)
        for (i in 1..5) {
            mockMvc.perform(
                post("/api/v1/notifications/tokens")
                    .header("Authorization", authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"platform": "ANDROID", "fcmToken": "token-$i", "deviceId": "device-$i"}""")
            )
                .andExpect(status().isCreated)
        }

        // 6th should fail
        mockMvc.perform(
            post("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"platform": "ANDROID", "fcmToken": "token-6", "deviceId": "device-6"}""")
        )
            .andExpect(status().isConflict)
    }

    // --- Token Listing ---

    @Test
    fun `listTokens returns all user tokens`() {
        // Register 2 tokens
        for (i in 1..2) {
            mockMvc.perform(
                post("/api/v1/notifications/tokens")
                    .header("Authorization", authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"platform": "ANDROID", "fcmToken": "token-$i", "deviceId": "device-$i"}""")
            )
                .andExpect(status().isCreated)
        }

        mockMvc.perform(
            get("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    // --- Token Deletion ---

    @Test
    fun `deleteToken removes specific token`() {
        // Register 2 tokens
        for (i in 1..2) {
            mockMvc.perform(
                post("/api/v1/notifications/tokens")
                    .header("Authorization", authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"platform": "ANDROID", "fcmToken": "token-$i", "deviceId": "device-$i"}""")
            )
                .andExpect(status().isCreated)
        }

        // Delete device-1
        mockMvc.perform(
            delete("/api/v1/notifications/tokens/device-1")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isNoContent)

        // Should have 1 remaining
        mockMvc.perform(
            get("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `deleteAllTokens removes all user tokens`() {
        // Register 2 tokens
        for (i in 1..2) {
            mockMvc.perform(
                post("/api/v1/notifications/tokens")
                    .header("Authorization", authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"platform": "ANDROID", "fcmToken": "token-$i", "deviceId": "device-$i"}""")
            )
                .andExpect(status().isCreated)
        }

        // Delete all
        mockMvc.perform(
            delete("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isNoContent)

        // Should have 0
        mockMvc.perform(
            get("/api/v1/notifications/tokens")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // --- Send Endpoints ---

    @Test
    fun `sendToToken returns 202 Accepted`() {
        mockMvc.perform(
            post("/api/v1/notifications/send/token")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token": "test-token", "title": "Test", "body": "Test body"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.notificationId").exists())
    }

    @Test
    fun `sendMulticast returns 202 Accepted`() {
        mockMvc.perform(
            post("/api/v1/notifications/send/multicast")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tokens": ["t1", "t2"], "title": "Test", "body": "Test body"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.notificationId").exists())
    }

    @Test
    fun `sendToTopic requires existing topic`() {
        mockMvc.perform(
            post("/api/v1/notifications/send/topic")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"topic": "nonexistent", "title": "Test", "body": "Body"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `sendToTopic returns 202 for existing topic`() {
        notificationTopicRepository.save(NotificationTopic("news"))

        mockMvc.perform(
            post("/api/v1/notifications/send/topic")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"topic": "news", "title": "Test", "body": "Body"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.notificationId").exists())
    }

    // --- History ---

    @Test
    fun `getHistory returns paginated results`() {
        // Create 3 history records directly
        for (i in 1..3) {
            val history = NotificationHistory(testUser.id, NotificationType.SINGLE, "token-$i", "Title $i", "Body $i")
            history.status = NotificationStatus.SENT
            notificationHistoryRepository.save(history)
        }

        // First page of 2
        val result = mockMvc.perform(
            get("/api/v1/notifications/history")
                .param("size", "2")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn()

        // Extract cursor from last item (UUID v7 is time-ordered)
        val body = result.response.contentAsString
        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val items = mapper.readTree(body)
        val cursorId = items.get(1).get("id").asText()

        // Second page using cursor
        mockMvc.perform(
            get("/api/v1/notifications/history")
                .param("cursor", cursorId)
                .param("size", "2")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    // --- Authentication ---

    @Test
    fun `unauthenticated request returns 401`() {
        mockMvc.perform(get("/api/v1/notifications/tokens"))
            .andExpect(status().isUnauthorized)
    }
}
