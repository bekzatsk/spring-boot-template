package kz.innlab.template

import kz.innlab.template.authentication.repository.RefreshTokenRepository
import kz.innlab.template.authentication.service.TokenService
import kz.innlab.template.notification.repository.NotificationPreferenceRepository
import kz.innlab.template.notification.service.PushService
import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.Role
import kz.innlab.template.user.model.User
import kz.innlab.template.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class NotificationPreferenceIntegrationTest {

    @MockitoBean
    private lateinit var pushService: PushService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var notificationPreferenceRepository: NotificationPreferenceRepository

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var accessToken: String
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        notificationPreferenceRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()

        testUser = userRepository.save(
            User(email = "pref-test@example.com").also {
                it.providers.add(AuthProvider.LOCAL)
            }
        )
        accessToken = tokenService.generateAccessToken(testUser.id, setOf(Role.USER))
    }

    private fun authHeader() = "Bearer $accessToken"

    @Test
    fun `getPreferences returns defaults when no preferences set`() {
        mockMvc.perform(
            get("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.push").value(true))
            .andExpect(jsonPath("$.email").value(true))
    }

    @Test
    fun `updatePreferences disables push`() {
        mockMvc.perform(
            put("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push": false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.push").value(false))
            .andExpect(jsonPath("$.email").value(true))

        // Verify persistence via GET
        mockMvc.perform(
            get("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.push").value(false))
            .andExpect(jsonPath("$.email").value(true))
    }

    @Test
    fun `updatePreferences disables email`() {
        mockMvc.perform(
            put("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.push").value(true))
            .andExpect(jsonPath("$.email").value(false))
    }

    @Test
    fun `updatePreferences partial update only changes specified`() {
        // Disable push
        mockMvc.perform(
            put("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push": false}""")
        )
            .andExpect(status().isOk)

        // Disable email (push should remain false)
        mockMvc.perform(
            put("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.push").value(false))
            .andExpect(jsonPath("$.email").value(false))
    }

    @Test
    fun `updatePreferences re-enables channel`() {
        // Disable push
        mockMvc.perform(
            put("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push": false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.push").value(false))

        // Re-enable push
        mockMvc.perform(
            put("/api/v1/notifications/preferences")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push": true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.push").value(true))
    }

    @Test
    fun `unauthenticated request returns 401`() {
        mockMvc.perform(get("/api/v1/notifications/preferences"))
            .andExpect(status().isUnauthorized)
    }
}
