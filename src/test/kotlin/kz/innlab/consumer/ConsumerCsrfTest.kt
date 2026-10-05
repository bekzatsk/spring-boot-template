package kz.innlab.consumer

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import jakarta.servlet.http.Cookie
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.user.model.Role
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** A consumer's own state-changing endpoint, as a downstream app would write one. */
@RestController
class ConsumerCrmController {
    @PostMapping("/api/crm/notes")
    fun create(): ResponseEntity<Void> = ResponseEntity.noContent().build()

    @PatchMapping("/api/crm/notes/1")
    fun update(): ResponseEntity<Void> = ResponseEntity.noContent().build()

    @GetMapping("/api/crm/notes")
    fun list(@AuthenticationPrincipal jwt: Jwt): Map<String, String?> = mapOf("sub" to jwt.subject)
}

@SpringBootTest(
    classes = [ConsumerApplication::class],
    properties = ["app.auth.cookie.enabled=true"]
)
@AutoConfigureMockMvc
class ConsumerCsrfTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var tokenService: TokenService

    private val token by lazy { tokenService.generateAccessToken(UUID.randomUUID(), setOf(Role.USER)) }

    @Test
    fun `cookie-authenticated write to a consumer endpoint without a CSRF token is refused`() {
        mockMvc.perform(post("/api/crm/notes").cookie(Cookie("access_token", token)))
            .andExpect(status().isForbidden)
        mockMvc.perform(patch("/api/crm/notes/1").cookie(Cookie("access_token", token)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a CSRF token lets the cookie-authenticated write through`() {
        val csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn()
        val csrfCookie = csrf.response.getCookie("XSRF-TOKEN")!!
        val csrfToken = JsonMapper.builder().build().readTree(csrf.response.contentAsString).get("token").asString()

        mockMvc.perform(
            post("/api/crm/notes")
                .cookie(Cookie("access_token", token), csrfCookie)
                .header("X-XSRF-TOKEN", csrfToken)
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `an explicit Authorization header needs no CSRF token`() {
        // A cross-site page cannot make a browser send an Authorization header, so a request
        // carrying one is not forgeable even if the cookies ride along.
        mockMvc.perform(
            post("/api/crm/notes").cookie(Cookie("access_token", token)).header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `bearer-only write needs no CSRF token`() {
        mockMvc.perform(post("/api/crm/notes").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `the access cookie alone still authenticates reads`() {
        mockMvc.perform(get("/api/crm/notes")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/crm/notes").cookie(Cookie("access_token", token)))
            .andExpect(status().isOk)
    }
}
