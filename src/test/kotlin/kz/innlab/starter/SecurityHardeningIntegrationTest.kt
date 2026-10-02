package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.notification.repository.DeviceTokenRepository
import kz.innlab.starter.notification.service.PushService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Regression tests for the hardening pass: API docs closed by default, roles changes ending
 * sessions, and FCM tokens owned by exactly one user.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningIntegrationTest {

    @MockitoBean private lateinit var pushService: PushService
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired private lateinit var refreshTokenService: RefreshTokenService
    @Autowired private lateinit var tokenService: TokenService

    private lateinit var admin: User
    private lateinit var member: User

    @BeforeEach
    fun setUp() {
        deviceTokenRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        admin = userRepository.save(User(email = "admin@example.com").also {
            it.linkProvider(AuthProvider.LOCAL)
            it.roles.add(Role.ADMIN)
        })
        member = userRepository.save(User(email = "member@example.com").also {
            it.linkProvider(AuthProvider.LOCAL)
        })
        `when`(pushService.sendToToken(anyString(), anyString(), anyString(), anyMap())).thenReturn("id")
    }

    private fun bearer(user: User, vararg roles: Role) =
        "Bearer ${tokenService.generateAccessToken(user.id, roles.toSet())}"

    @Test
    fun `API docs require authentication unless made public`() {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `changing roles revokes the user's refresh tokens`() {
        val refreshToken = refreshTokenService.createToken(member)

        mockMvc.perform(
            patch("/api/v1/admin/users/${member.id}/roles")
                .header("Authorization", bearer(admin, Role.USER, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"roles": ["USER", "ADMIN"]}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `registering a token another user holds takes it over`() {
        val register = { user: User, deviceId: String ->
            mockMvc.perform(
                post("/api/v1/notifications/tokens")
                    .header("Authorization", bearer(user, Role.USER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"platform": "ANDROID", "fcmToken": "shared-device-token", "deviceId": "$deviceId"}""")
            ).andExpect(status().isCreated)
        }
        register(admin, "phone-a")
        register(member, "phone-b")

        assert(deviceTokenRepository.findByFcmToken("shared-device-token")?.userId == member.id)
        assert(deviceTokenRepository.findByUserId(admin.id).isEmpty())

        mockMvc.perform(
            post("/api/v1/notifications/send/token")
                .header("Authorization", bearer(admin, Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token": "shared-device-token", "title": "T", "body": "B"}""")
        ).andExpect(status().isForbidden)
    }
}

/** A broad consumer public path must not open the admin-only endpoints. */
@SpringBootTest(properties = ["app.auth.security.public-paths=/api/**"])
@AutoConfigureMockMvc
class PublicPathsCannotOpenAdminIntegrationTest {

    @MockitoBean private lateinit var pushService: PushService
    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `admin endpoints stay protected under a catch-all public path`() {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/mail/inbox")).andExpect(status().isUnauthorized)
    }
}
