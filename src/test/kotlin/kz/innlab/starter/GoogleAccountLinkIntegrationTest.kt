package kz.innlab.starter

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.json.webtoken.JsonWebSignature
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

abstract class GoogleLinkTestBase {

    @MockitoBean
    lateinit var googleIdTokenVerifier: GoogleIdTokenVerifier

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var refreshTokens: RefreshTokenRepository
    @Autowired lateinit var tokenService: TokenService

    @BeforeEach
    fun cleanUp() {
        refreshTokens.deleteAll()
        users.deleteAll()
    }

    /** Makes [token] verify as a Google account [sub] with [email]. */
    fun googleToken(token: String, sub: String, email: String, emailVerified: Boolean = true) {
        val payload = GoogleIdToken.Payload().setSubject(sub).setEmail(email).setEmailVerified(emailVerified)
        val idToken = GoogleIdToken(JsonWebSignature.Header(), payload, ByteArray(0), ByteArray(0))
        `when`(googleIdTokenVerifier.verify(token)).thenReturn(idToken)
    }

    fun link(user: User, idToken: String): ResultActions = mockMvc.perform(
        post("/api/v1/users/me/providers/google")
            .header("Authorization", "Bearer ${tokenService.generateAccessToken(user.id, user.roles)}")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"idToken":"$idToken"}""")
    )

    fun googleLogin(idToken: String): ResultActions = mockMvc.perform(
        post("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"idToken":"$idToken"}""")
    )

    fun invitedUser(email: String, verified: Boolean = true, admin: Boolean = false): User =
        users.save(User(email).apply {
            linkProvider(AuthProvider.LOCAL)
            emailVerified = verified
            roles.add(Role.USER)
            if (admin) roles.add(Role.ADMIN)
        })
}

@SpringBootTest
@AutoConfigureMockMvc
class GoogleAccountLinkIntegrationTest : GoogleLinkTestBase() {

    @Test
    fun `signed-in user links a Google account with the same email`() {
        val user = invitedUser("Invited@Example.com", verified = false)
        googleToken("t1", "sub-1", "invited@example.com")

        link(user, "t1")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers").value(org.hamcrest.Matchers.hasItem("GOOGLE")))

        val saved = users.findById(user.id).orElseThrow()
        assertEquals("sub-1", saved.providerIds[AuthProvider.GOOGLE])
        assertTrue(saved.emailVerified)

        // The linked Google account now signs in to the same user.
        googleLogin("t1").andExpect(status().isOk)
        assertEquals(1L, users.count())
    }

    @Test
    fun `linking is refused when the Google email differs`() {
        val user = invitedUser("owner@example.com")
        googleToken("t2", "sub-2", "someone-else@example.com")

        link(user, "t2").andExpect(status().isForbidden)
        assertNull(users.findById(user.id).orElseThrow().providerIds[AuthProvider.GOOGLE])
    }

    @Test
    fun `linking is refused when Google has not verified the email`() {
        val user = invitedUser("owner@example.com")
        googleToken("t3", "sub-3", "owner@example.com", emailVerified = false)

        link(user, "t3").andExpect(status().isUnauthorized)
    }

    @Test
    fun `a Google account linked to another user answers 409`() {
        users.save(User("first@example.com").apply { linkProvider(AuthProvider.GOOGLE, "sub-4") })
        val user = invitedUser("second@example.com")
        // Same Google subject, whose email has since changed to the second user's address.
        googleToken("t4", "sub-4", "second@example.com")

        link(user, "t4").andExpect(status().isConflict)
    }

    @Test
    fun `a second Google account cannot replace the linked one`() {
        val user = users.save(User("owner@example.com").apply {
            linkProvider(AuthProvider.GOOGLE, "sub-old")
            roles.add(Role.USER)
        })
        googleToken("t5", "sub-new", "owner@example.com")

        link(user, "t5").andExpect(status().isConflict)
        assertEquals("sub-old", users.findById(user.id).orElseThrow().providerIds[AuthProvider.GOOGLE])
    }

    @Test
    fun `linking needs a signed-in user`() {
        mockMvc.perform(
            post("/api/v1/users/me/providers/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"t"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `without the flag Google sign-in still refuses an existing email`() {
        invitedUser("invited@example.com")
        googleToken("t6", "sub-6", "invited@example.com")

        googleLogin("t6").andExpect(status().isConflict)
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.auth.google.link-by-verified-email=true"])
class GoogleLinkByVerifiedEmailIntegrationTest : GoogleLinkTestBase() {

    @Test
    fun `Google sign-in links an invited user with the same verified email`() {
        val user = invitedUser("invited@example.com")
        googleToken("t1", "sub-1", "Invited@Example.com")

        googleLogin("t1").andExpect(status().isOk)

        assertEquals("sub-1", users.findById(user.id).orElseThrow().providerIds[AuthProvider.GOOGLE])
        assertEquals(1L, users.count())
    }

    @Test
    fun `an unverified account is not taken over by email`() {
        val user = invitedUser("unverified@example.com", verified = false)
        googleToken("t2", "sub-2", "unverified@example.com")

        googleLogin("t2").andExpect(status().isConflict)
        assertNull(users.findById(user.id).orElseThrow().providerIds[AuthProvider.GOOGLE])
    }

    @Test
    fun `an administrator is not linked by email`() {
        val admin = invitedUser("admin@example.com", admin = true)
        googleToken("t3", "sub-3", "admin@example.com")

        googleLogin("t3").andExpect(status().isConflict)
        assertNull(users.findById(admin.id).orElseThrow().providerIds[AuthProvider.GOOGLE])
    }

    @Test
    fun `an account with another Google account linked is not relinked`() {
        val user = users.save(User("owner@example.com").apply {
            linkProvider(AuthProvider.GOOGLE, "sub-old")
            roles.add(Role.USER)
        })
        googleToken("t4", "sub-new", "owner@example.com")

        googleLogin("t4").andExpect(status().isConflict)
        assertEquals("sub-old", users.findById(user.id).orElseThrow().providerIds[AuthProvider.GOOGLE])
    }
}
