package kz.innlab.template

import kz.innlab.template.authentication.repository.RefreshTokenRepository
import kz.innlab.template.authentication.service.TokenService
import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.User
import kz.innlab.template.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AppleAuthIntegrationTest {

    @MockitoBean(name = "appleJwtDecoder")
    private lateinit var appleJwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun cleanUp() {
        // Delete refresh tokens first to avoid FK constraint violation
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun buildMockJwt(
        sub: String,
        email: String? = null,
        bundleId: String = "test-bundle-id"
    ): Jwt {
        val builder = Jwt.withTokenValue("mock-apple-token")
            .header("alg", "RS256")
            .claim("sub", sub)
            .claim("iss", "https://appleid.apple.com")
            .claim("aud", listOf(bundleId))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))

        if (email != null) {
            builder.claim("email", email)
        }

        return builder.build()
    }

    @Test
    fun `first sign-in with name creates user and returns tokens`() {
        val appleUserId = "apple-sub-${UUID.randomUUID()}"
        val email = "jane.doe@example.com"
        val mockJwt = buildMockJwt(sub = appleUserId, email = email)

        `when`(appleJwtDecoder.decode(anyString())).thenReturn(mockJwt)

        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken": "valid-token", "givenName": "Jane", "familyName": "Doe"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        val user = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, appleUserId)
        assert(user != null) { "User should be created" }
        assert(user!!.email == email) { "Email should match" }
        assert(user.name == "Jane Doe") { "Name should be 'Jane Doe' but was '${user.name}'" }
        assert(user.provider == AuthProvider.APPLE) { "Provider should be APPLE" }
    }

    @Test
    fun `subsequent sign-in without email finds existing user and returns tokens`() {
        val appleUserId = "apple-sub-${UUID.randomUUID()}"

        // Pre-create an APPLE user (simulating a previously registered user)
        val existingUser = userRepository.save(
            User(
                email = "existing@example.com",
                provider = AuthProvider.APPLE,
                providerId = appleUserId
            )
        )

        // Subsequent login: JWT has sub but NO email claim
        val mockJwt = buildMockJwt(sub = appleUserId, email = null)
        `when`(appleJwtDecoder.decode(anyString())).thenReturn(mockJwt)

        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken": "valid-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        // No duplicate user created
        val users = userRepository.findAll().filter { it.provider == AuthProvider.APPLE }
        assert(users.size == 1) { "Should be exactly 1 APPLE user, found ${users.size}" }
        assert(users[0].id == existingUser.id) { "Should be the same user" }
    }

    @Test
    fun `private relay email accepted on first sign-in`() {
        val appleUserId = "apple-sub-${UUID.randomUUID()}"
        val privateRelayEmail = "user@privaterelay.appleid.com"
        val mockJwt = buildMockJwt(sub = appleUserId, email = privateRelayEmail)

        `when`(appleJwtDecoder.decode(anyString())).thenReturn(mockJwt)

        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken": "valid-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        // AUTH-06: private relay email stored as-is without rejection
        val user = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, appleUserId)
        assert(user != null) { "User should be created with private relay email" }
        assert(user!!.email == privateRelayEmail) { "Private relay email should be stored" }
    }

    @Test
    fun `invalid token returns 401`() {
        `when`(appleJwtDecoder.decode(anyString()))
            .thenThrow(JwtException("Invalid signature"))

        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken": "invalid-token"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.status").value(401))
    }
}
