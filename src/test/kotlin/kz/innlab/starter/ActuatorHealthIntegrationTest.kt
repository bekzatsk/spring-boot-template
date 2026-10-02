package kz.innlab.starter

import java.util.UUID
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.authentication.service.TokenService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Smoke test verifying spring-boot-starter-actuator is wired transitively.
 * /actuator/health must respond 200 UP out of the box so consumer docker
 * healthchecks (depends_on healthy) work without extra config.
 */
@SpringBootTest(properties = ["management.endpoints.web.exposure.include=health,env"])
@AutoConfigureMockMvc
class ActuatorHealthIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tokenService: TokenService

    @Test
    fun `actuator health endpoint returns 200 UP`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `other actuator endpoints are admin-only`() {
        val user = tokenService.generateAccessToken(UUID.randomUUID(), setOf(Role.USER))
        val admin = tokenService.generateAccessToken(UUID.randomUUID(), setOf(Role.USER, Role.ADMIN))

        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer $user"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer $admin"))
            .andExpect(status().isOk)
    }
}
