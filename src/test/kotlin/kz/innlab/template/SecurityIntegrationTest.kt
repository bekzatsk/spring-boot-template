package kz.innlab.template

import jakarta.validation.Valid
import kz.innlab.template.authentication.JwtTokenService
import kz.innlab.template.authentication.dto.AuthRequest
import kz.innlab.template.user.AuthProvider
import kz.innlab.template.user.Role
import kz.innlab.template.user.User
import kz.innlab.template.user.UserRepository
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.ValidationTestController::class)
class SecurityIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

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
            User(
                email = "test@example.com",
                provider = AuthProvider.GOOGLE,
                providerId = "google-sub-${UUID.randomUUID()}"
            )
        )
        val token = jwtTokenService.generateAccessToken(user.id!!, setOf(Role.USER))
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.roles[0]").value("USER"))
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
            User(
                email = "test2@example.com",
                provider = AuthProvider.GOOGLE,
                providerId = "google-sub-${UUID.randomUUID()}"
            )
        )
        val token = jwtTokenService.generateAccessToken(user.id!!, setOf(Role.USER))

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
