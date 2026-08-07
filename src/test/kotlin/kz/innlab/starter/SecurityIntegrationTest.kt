package kz.innlab.starter

import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AuthRequest
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(
    SecurityIntegrationTest.ValidationTestController::class,
    SecurityIntegrationTest.ConsumerPathTestController::class
)
class SecurityIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var userRepository: UserRepository

    @TestConfiguration
    @RestController
    @RequestMapping("/api/v1/test-validation")
    class ValidationTestController {
        @PostMapping
        fun validate(@Valid @RequestBody request: AuthRequest): ResponseEntity<String> =
            ResponseEntity.ok("ok")
    }

    // Stands in for an endpoint a consumer application adds outside the starter's /api/** namespace.
    @TestConfiguration
    @RestController
    @RequestMapping("/consumer-endpoint")
    class ConsumerPathTestController {
        @GetMapping
        fun ping(): ResponseEntity<String> = ResponseEntity.ok("pong")
    }

    @Test
    fun `noToken returns 401 with JSON body`() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `validToken returns 200 with user profile`() {
        val user = userRepository.save(
            User(email = "test@example.com").also {
                it.providers.add(AuthProvider.GOOGLE)
                it.providerIds[AuthProvider.GOOGLE] = "google-sub-${UUID.randomUUID()}"
            }
        )
        val token = tokenService.generateAccessToken(user.id, setOf(Role.USER))
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.roles[0]").value("USER"))
    }

    // --- Fail-secure default authorization ---

    @Test
    fun `consumer path outside api namespace requires authentication`() {
        // Previously `anyRequest permitAll` made every such path public by default.
        mockMvc.perform(get("/consumer-endpoint"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `consumer path outside api namespace is reachable with a token`() {
        val user = userRepository.save(
            User(email = "consumer-path@example.com").also { it.providers.add(AuthProvider.LOCAL) }
        )
        val token = tokenService.generateAccessToken(user.id, setOf(Role.USER))

        mockMvc.perform(get("/consumer-endpoint").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `unmapped path is not public by default`() {
        mockMvc.perform(get("/definitely-not-mapped"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `actuator health stays public for container healthchecks`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `corsPreflight returns 200 with CORS headers`() {
        mockMvc.perform(
            options("/api/v1/users/me")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
    }

    @Test
    fun `blankIdToken returns 400 with validation error`() {
        val user = userRepository.save(
            User(email = "test2@example.com").also {
                it.providers.add(AuthProvider.GOOGLE)
                it.providerIds[AuthProvider.GOOGLE] = "google-sub-${UUID.randomUUID()}"
            }
        )
        val token = tokenService.generateAccessToken(user.id, setOf(Role.USER))

        // Test with blank idToken
        mockMvc.perform(
            post("/api/v1/test-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken": ""}""")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))

        // Test with missing idToken field
        mockMvc.perform(
            post("/api/v1/test-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
    }
}
