package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.shared.ratelimit.RateLimiter
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Both endpoints check a secret the caller is trying to guess, and neither was limited: the
 * password check on login was an unlimited oracle, and change-password let a stolen access
 * token be escalated into a permanent takeover by guessing the current password.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "app.auth.rate-limit.login.max-attempts=3",
        "app.auth.rate-limit.login.window-seconds=300",
        "app.auth.rate-limit.change-password.max-attempts=2",
        "app.auth.rate-limit.change-password.window-seconds=300"
    ]
)
class RateLimitIntegrationTest {

    @MockitoBean
    private lateinit var emailService: EmailService

    @MockitoBean
    private lateinit var smsService: SmsService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var rateLimiter: RateLimiter

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        // The limiter is a singleton and keeps counters across tests in the same context.
        rateLimiter.reset("login:limited@example.com")
        user = userRepository.save(
            User(email = "limited@example.com").also {
                it.linkProvider(AuthProvider.LOCAL)
                it.passwordHash = passwordEncoder.encode("CorrectPassword1")
            }
        )
    }

    private fun login(password: String) = mockMvc.perform(
        post("/api/v1/auth/local/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email": "limited@example.com", "password": "$password"}""")
    )

    private fun changePassword(token: String, current: String) = mockMvc.perform(
        post("/api/v1/users/me/change-password")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"currentPassword": "$current", "newPassword": "BrandNewPassword1"}""")
    )

    @Test
    fun `login stops answering after the attempt limit and reports Retry-After`() {
        repeat(3) { login("wrong-$it").andExpect(status().isUnauthorized) }

        login("another-wrong")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `the correct password is refused too once the limit is hit`() {
        repeat(3) { login("wrong-$it").andExpect(status().isUnauthorized) }

        // Otherwise the limit could be sidestepped by guessing until the right one is found.
        login("CorrectPassword1").andExpect(status().isTooManyRequests)
    }

    @Test
    fun `a successful login clears the counter`() {
        login("wrong-once").andExpect(status().isUnauthorized)
        login("CorrectPassword1").andExpect(status().isOk)

        // The earlier failure must not count against the user any more.
        repeat(3) { login("wrong-again-$it").andExpect(status().isUnauthorized) }
    }

    @Test
    fun `change-password stops answering after the attempt limit`() {
        val token = tokenService.generateAccessToken(user.id, setOf(Role.USER))
        rateLimiter.reset("change-password:${user.id}")

        repeat(2) { changePassword(token, "wrong-$it").andExpect(status().isUnauthorized) }

        changePassword(token, "wrong-again")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }
}
