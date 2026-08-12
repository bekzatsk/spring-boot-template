package kz.innlab.consumer

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The cookie feature, exercised over HTTP from a consumer application.
 *
 * `CookieAuthIntegrationTest` covers the same flows in depth but boots `AuthStarterApplication`,
 * whose package scan creates `AuthCookieWriter` whether or not the auto-configuration declares it.
 * This test boots [ConsumerApplication] instead, so what it asserts is that a released artifact
 * emits Set-Cookie — the exact check that failed for a consumer on 0.1.0 while the suite was green.
 */
@SpringBootTest(
    classes = [ConsumerApplication::class],
    properties = [
        "app.auth.cookie.enabled=true",
        "app.auth.cookie.secure=true",
        "app.auth.cookie.same-site=Strict"
    ]
)
@AutoConfigureMockMvc
class ConsumerCookieAuthIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun assertAuthCookie(setCookies: List<String>, name: String) {
        val header = setCookies.firstOrNull { it.startsWith("$name=") }
        checkNotNull(header) { "no Set-Cookie for '$name' — cookie mode is dead: $setCookies" }
        assert(header.contains("HttpOnly")) { "'$name' must be HttpOnly: $header" }
        assert(header.contains("Secure")) { "'$name' must be Secure: $header" }
        assert(header.contains("SameSite=Strict")) { "'$name' must carry SameSite=Strict: $header" }
    }

    @Test
    fun `login emits both auth cookies with HttpOnly, Secure and SameSite`() {
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "consumer@example.com", "password": "SecurePass123"}""")
        ).andExpect(status().isCreated)

        val response = mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "consumer@example.com", "password": "SecurePass123"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
            .response

        val setCookies = response.getHeaders("Set-Cookie")
        assertAuthCookie(setCookies, "access_token")
        assertAuthCookie(setCookies, "refresh_token")
    }

    @Test
    fun `refresh rotates the cookie pair`() {
        val registered = mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "rotate@example.com", "password": "SecurePass123"}""")
        ).andExpect(status().isCreated).andReturn().response

        val refresh = registered.getCookie("refresh_token")
        checkNotNull(refresh) { "register must set a refresh cookie in cookie mode" }

        val rotated = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
            .andExpect(status().isOk)
            .andReturn()
            .response

        assertAuthCookie(rotated.getHeaders("Set-Cookie"), "access_token")
        val newRefresh = rotated.getCookie("refresh_token")
        checkNotNull(newRefresh)
        assert(newRefresh.value.isNotBlank() && newRefresh.value != refresh.value) {
            "refresh cookie must be rotated to a new value"
        }
    }
}
