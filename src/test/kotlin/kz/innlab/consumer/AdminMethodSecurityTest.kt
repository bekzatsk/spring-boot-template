package kz.innlab.consumer

import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.user.model.Role
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * With the starter's filter chain switched off, Boot's fallback chain only asks for a login. The
 * admin handlers must still refuse a plain user on their own.
 */
@SpringBootTest(
    classes = [ConsumerApplication::class],
    properties = ["app.auth.security.enabled=false"]
)
@AutoConfigureMockMvc
class AdminMethodSecurityTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var tokenService: TokenService

    @Test
    fun `admin endpoints refuse a plain user even without the starter's filter chain`() {
        val userToken = tokenService.generateAccessToken(UUID.randomUUID(), setOf(Role.USER))

        listOf("/api/v1/admin/users", "/api/v1/mail/inbox").forEach { path ->
            mockMvc.perform(get(path).header("Authorization", "Bearer $userToken"))
                .andExpect(status().isForbidden)
        }
    }
}
