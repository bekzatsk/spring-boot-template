package kz.innlab.starter

import jakarta.servlet.http.Cookie
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "app.auth.cookie.enabled=true",
        "app.auth.cookie.secure=true",
        "app.auth.cookie.same-site=Strict"
    ]
)
@AutoConfigureMockMvc
class CookieAuthIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun register(email: String): Pair<Cookie, Cookie> {
        val result = mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "$email", "password": "SecurePass123"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()
        val access = result.response.getCookie("access_token")!!
        val refresh = result.response.getCookie("refresh_token")!!
        return access to refresh
    }

    @Test
    fun `login sets two httpOnly cookies with correct attributes`() {
        register("cookie@example.com")

        val result = mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "cookie@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isOk)
            // body tokens still present (suppress-body-tokens defaults false)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(cookie().httpOnly("access_token", true))
            .andExpect(cookie().secure("access_token", true))
            .andExpect(cookie().httpOnly("refresh_token", true))
            .andExpect(cookie().secure("refresh_token", true))
            .andReturn()

        val setCookies = result.response.getHeaders("Set-Cookie")
        assert(setCookies.any { it.startsWith("access_token=") && it.contains("SameSite=Strict") }) {
            "access_token cookie must carry SameSite=Strict: $setCookies"
        }
        assert(setCookies.any { it.startsWith("refresh_token=") && it.contains("HttpOnly") }) {
            "refresh_token cookie must be HttpOnly: $setCookies"
        }
    }

    @Test
    fun `access cookie authenticates request without Authorization header`() {
        val (access, _) = register("me@example.com")

        mockMvc.perform(
            get("/api/v1/users/me").cookie(access)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("me@example.com"))
    }

    @Test
    fun `refresh from cookie rotates and re-sets cookies`() {
        val (_, refresh) = register("refresh@example.com")

        val result = mockMvc.perform(
            post("/api/v1/auth/refresh").cookie(refresh)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"))
            .andReturn()

        val newRefresh = result.response.getCookie("refresh_token")!!.value
        assert(newRefresh.isNotBlank() && newRefresh != refresh.value) {
            "refresh cookie must be rotated to a new value"
        }
    }

    @Test
    fun `reusing refresh cookie within grace window returns 409`() {
        val (_, refresh) = register("grace@example.com")

        // First rotation succeeds
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
            .andExpect(status().isOk)

        // Immediate reuse of the same (now revoked) token — within 10s grace window → 409
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
            .andExpect(status().isConflict)
    }

    @Test
    fun `logout revokes refresh token and clears cookies`() {
        val (_, refresh) = register("logout@example.com")

        val result = mockMvc.perform(
            post("/api/v1/auth/logout").cookie(refresh)
        )
            .andExpect(status().isNoContent)
            .andReturn()

        val setCookies = result.response.getHeaders("Set-Cookie")
        assert(setCookies.any { it.startsWith("access_token=") && it.contains("Max-Age=0") }) {
            "logout must expire access cookie: $setCookies"
        }
        assert(setCookies.any { it.startsWith("refresh_token=") && it.contains("Max-Age=0") }) {
            "logout must expire refresh cookie: $setCookies"
        }

        // Token is revoked — reusing it now fails (reuse detection revoked it)
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout without cookie is idempotent 204`() {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isNoContent)
    }
}

/**
 * Backward-compat guard: with cookie mode disabled (default), no Set-Cookie header appears and
 * the body-token contract is untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CookieDisabledIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `login does not emit Set-Cookie when cookie mode disabled`() {
        val result = mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "nocookie@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andReturn()

        assert(result.response.getHeaders("Set-Cookie").isEmpty()) {
            "No Set-Cookie header expected in default (body-token) mode"
        }
    }
}
