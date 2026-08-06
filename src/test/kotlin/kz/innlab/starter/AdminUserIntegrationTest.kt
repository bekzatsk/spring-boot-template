package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.notification.service.PushService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTest {

    @MockitoBean
    private lateinit var pushService: PushService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()

        val admin = userRepository.save(
            User(email = "admin@example.com").also {
                it.name = "Root Admin"
                it.providers.add(AuthProvider.LOCAL)
                it.roles.add(Role.ADMIN)
            }
        )
        val member = userRepository.save(
            User(email = "member@example.com").also {
                it.name = "Regular Member"
                it.providers.add(AuthProvider.LOCAL)
            }
        )

        adminToken = tokenService.generateAccessToken(admin.id, setOf(Role.USER, Role.ADMIN))
        userToken = tokenService.generateAccessToken(member.id, setOf(Role.USER))
    }

    @Test
    fun `list returns summary page for admin`() {
        mockMvc.perform(
            get("/api/v1/admin/users").header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").exists())
            .andExpect(jsonPath("$.content[0].email").exists())
    }

    @Test
    fun `list filters by search query`() {
        mockMvc.perform(
            get("/api/v1/admin/users")
                .param("q", "member")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].email").value("member@example.com"))
    }

    @Test
    fun `search treats LIKE wildcards as literal characters`() {
        // "%" used to match every row and force a full scan.
        mockMvc.perform(
            get("/api/v1/admin/users")
                .param("q", "%")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `list is forbidden for non-admin`() {
        mockMvc.perform(
            get("/api/v1/admin/users").header("Authorization", "Bearer $userToken")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `get unknown user returns 404`() {
        mockMvc.perform(
            get("/api/v1/admin/users/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }
}
