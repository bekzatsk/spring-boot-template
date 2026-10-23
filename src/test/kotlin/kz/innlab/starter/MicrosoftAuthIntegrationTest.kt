package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@SpringBootTest(
    properties = [
        "app.auth.microsoft.enabled=true",
        "app.auth.microsoft.tenant-id=$TENANT",
        "app.auth.microsoft.client-id=test-client",
        "app.auth.microsoft.access.required-groups=$USERS_GROUP",
        "app.auth.microsoft.access.admin-groups=$ADMINS_GROUP"
    ]
)
@AutoConfigureMockMvc
class MicrosoftAuthIntegrationTest {

    @MockitoBean(name = "microsoftJwtDecoder")
    private lateinit var microsoftJwtDecoder: JwtDecoder

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var jwtDecoder: JwtDecoder
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun token(
        oid: String = "11111111-2222-3333-4444-555555555555",
        username: String = "ivanov@corp.example",
        name: String = "Ivan Ivanov",
        groups: List<String> = listOf(USERS_GROUP)
    ): Jwt = Jwt.withTokenValue("mock-microsoft-token")
        .header("alg", "RS256")
        .claim("iss", "https://login.microsoftonline.com/$TENANT/v2.0")
        .claim("aud", listOf("test-client"))
        .claim("sub", "pairwise-subject")
        .claim("oid", oid)
        .claim("preferred_username", username)
        .claim("name", name)
        .claim("groups", groups)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build()

    private fun login() = mockMvc.perform(
        post("/api/v1/auth/microsoft")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"idToken": "valid-token"}""")
    )

    @Test
    fun `first sign-in creates a user linked by oid`() {
        `when`(microsoftJwtDecoder.decode(anyString())).thenReturn(token())

        val body = login().andExpect(status().isOk).andReturn().response.contentAsString

        val user = userRepository.findAll().single()
        assertThat(user.providerIds[AuthProvider.MICROSOFT]).isEqualTo("11111111-2222-3333-4444-555555555555")
        assertThat(user.email).isEqualTo("ivanov@corp.example")
        assertThat(user.name).isEqualTo("Ivan Ivanov")
        assertThat(user.roles).containsExactly(Role.USER)
        val accessToken = objectMapper.readTree(body)["accessToken"].asString()
        assertThat(jwtDecoder.decode(accessToken).getClaimAsStringList("groups")).containsExactly(USERS_GROUP)
    }

    @Test
    fun `a returning user is found by oid and updated`() {
        `when`(microsoftJwtDecoder.decode(anyString())).thenReturn(token())
        login().andExpect(status().isOk)

        `when`(microsoftJwtDecoder.decode(anyString()))
            .thenReturn(token(name = "Ivan I. Ivanov", groups = listOf(USERS_GROUP, ADMINS_GROUP)))
        login().andExpect(status().isOk)

        val user = userRepository.findAll().single()
        assertThat(user.name).isEqualTo("Ivan I. Ivanov")
        assertThat(user.roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
    }

    @Test
    fun `leaving the admin group takes ADMIN away`() {
        `when`(microsoftJwtDecoder.decode(anyString())).thenReturn(token(groups = listOf(USERS_GROUP, ADMINS_GROUP)))
        login().andExpect(status().isOk)
        `when`(microsoftJwtDecoder.decode(anyString())).thenReturn(token())
        login().andExpect(status().isOk)

        assertThat(userRepository.findAll().single().roles).containsExactly(Role.USER)
    }

    @Test
    fun `accounts outside the required groups cannot sign in`() {
        `when`(microsoftJwtDecoder.decode(anyString())).thenReturn(token(groups = listOf("some-other-group")))
        login().andExpect(status().isUnauthorized)
        assertThat(userRepository.findAll()).isEmpty()
    }

    @Test
    fun `an invalid token is refused`() {
        `when`(microsoftJwtDecoder.decode(anyString())).thenThrow(JwtException("bad signature"))
        login().andExpect(status().isUnauthorized)
    }

    @Test
    fun `an email held by another account is a conflict, not a takeover`() {
        userRepository.save(User(email = "ivanov@corp.example").also { it.linkProvider(AuthProvider.LOCAL) })
        `when`(microsoftJwtDecoder.decode(anyString())).thenReturn(token())
        login().andExpect(status().isConflict)
    }
}

private const val TENANT = "9188040d-6c67-4c5b-b112-36a304b66dad"
private const val USERS_GROUP = "aaaaaaaa-0000-0000-0000-000000000001"
private const val ADMINS_GROUP = "aaaaaaaa-0000-0000-0000-000000000002"
