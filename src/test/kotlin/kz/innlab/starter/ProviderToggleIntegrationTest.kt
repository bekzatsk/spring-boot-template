package kz.innlab.starter

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "app.auth.google.enabled=false",
    "app.auth.apple.enabled=false",
    "app.auth.local.enabled=false",
    "app.auth.phone.enabled=false"
])
class ProviderToggleIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `disabled google returns 404`() {
        mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"test"}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `disabled apple returns 404`() {
        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"test"}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `disabled local register returns 404`() {
        mockMvc.perform(
            post("/api/v1/auth/local/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","password":"12345678"}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `disabled local login returns 404`() {
        mockMvc.perform(
            post("/api/v1/auth/local/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","password":"12345678"}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `disabled phone request returns 404`() {
        mockMvc.perform(
            post("/api/v1/auth/phone/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone":"+77001234567"}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `disabled phone verify returns 404`() {
        mockMvc.perform(
            post("/api/v1/auth/phone/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"00000000-0000-0000-0000-000000000000","phone":"+77001234567","code":"123456"}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `disabled forgot-password returns 404`() {
        mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com"}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `refresh still works when providers disabled`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"invalid"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `revoke still works when providers disabled`() {
        mockMvc.perform(
            post("/api/v1/auth/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"invalid"}""")
        ).andExpect(status().isNoContent)
    }
}
