package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class LocalAuthIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository


    @BeforeEach
    fun cleanUp() {
        // Delete refresh tokens first to avoid FK constraint violation (Phase 04-01 decision)
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `register success returns 201 with access and refresh tokens`() {
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@example.com", "password": "SecurePass123", "name": "Test User"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        val user = userRepository.findByEmail("test@example.com")
        assert(user != null) { "User should be created in DB" }
        assert(AuthProvider.LOCAL in user!!.providers) { "Providers should contain LOCAL" }
        assert(user.passwordHash != null) { "passwordHash should be set" }
        assert(user.passwordHash != "SecurePass123") { "Password must not be stored in plaintext" }
        assert(user.passwordHash!!.startsWith("{bcrypt}")) { "Password should be BCrypt-hashed but was: ${user.passwordHash}" }
        assert(user.name == "Test User") { "Name should be stored" }
    }

    @Test
    fun `register duplicate email returns 409 Conflict`() {
        val registerJson = """{"email": "dup@example.com", "password": "SecurePass123"}"""

        // First registration
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        )
            .andExpect(status().isCreated)

        // Second registration with same email
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `anonymous registration cannot add password to existing social admin`() {
        val victim = userRepository.save(User(email = "social-admin@example.com").apply {
            linkProvider(AuthProvider.GOOGLE, "google-sub-123")
            roles.add(Role.ADMIN)
        })

        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"social-admin@example.com","password":"AttackerPassword123"}""")
        ).andExpect(status().isConflict)

        val unchanged = userRepository.findById(victim.id).orElseThrow()
        assert(unchanged.passwordHash == null)
        assert(AuthProvider.LOCAL !in unchanged.providers)
        assert(Role.ADMIN in unchanged.roles)
        assert(refreshTokenRepository.count() == 0L)

        mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"social-admin@example.com","password":"AttackerPassword123"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `registration rejects existing social email with different case`() {
        val victim = userRepository.save(User(email = "CaseSensitive@example.com").apply {
            linkProvider(AuthProvider.GOOGLE, "case-google-sub")
        })

        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"casesensitive@example.com","password":"AttackerPassword123"}""")
        ).andExpect(status().isConflict)

        assert(userRepository.findById(victim.id).orElseThrow().passwordHash == null)
        assert(userRepository.count() == 1L)
    }

    @Test
    fun `login success returns 200 with access and refresh tokens`() {
        // Register first
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "login@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isCreated)

        // Login
        mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "login@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
    }

    @Test
    fun `login wrong password returns 401`() {
        // Register first
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "wrongpw@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isCreated)

        // Login with wrong password
        mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "wrongpw@example.com", "password": "WrongPassword!"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `login non-existent email returns 401`() {
        mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "nobody@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `register with empty email returns 400`() {
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "", "password": "SecurePass123"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `register with short password returns 400`() {
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "shortpw@example.com", "password": "short"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `login ignores the letter case of the email`() {
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "case@example.com", "password": "SecurePass123"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "Case@Example.COM", "password": "SecurePass123"}""")
        ).andExpect(status().isOk)
    }
}
