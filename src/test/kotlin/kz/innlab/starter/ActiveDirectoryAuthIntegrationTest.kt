package kz.innlab.starter

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import com.unboundid.ldap.sdk.Modification
import com.unboundid.ldap.sdk.ModificationType
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.DirectoryClient
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.shared.ratelimit.RateLimiter
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.net.ServerSocket

/**
 * Active Directory login against a real LDAP server (UnboundID, in process). The server is not AD:
 * accounts are found by `uid`, the stable id is `entryUUID`, and `memberOf` / `userAccountControl`
 * are plain attributes — which is exactly what the attribute settings exist for.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActiveDirectoryAuthIntegrationTest {

    companion object {
        private const val BASE = "dc=corp,dc=local"
        private const val USERS_GROUP = "cn=App Users,ou=groups,$BASE"
        private const val ADMINS_GROUP = "cn=App Admins,ou=groups,$BASE"
        private const val ENABLED = "512"
        private const val DISABLED = "514"

        private val port = ServerSocket(0).use { it.localPort }

        private val server: InMemoryDirectoryServer = InMemoryDirectoryServer(
            InMemoryDirectoryServerConfig(BASE).apply {
                // No schema: lets the AD attributes (memberOf, userAccountControl) live on plain entries.
                schema = null
                setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", port))
            }
        ).also { it.startListening() }

        @JvmStatic
        @AfterAll
        fun stopDirectory() {
            server.shutDown(true)
        }

        @JvmStatic
        @DynamicPropertySource
        fun directoryProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.auth.active-directory.enabled") { "true" }
            registry.add("app.auth.active-directory.urls") { "ldap://localhost:$port" }
            registry.add("app.auth.active-directory.base-dn") { BASE }
            registry.add("app.auth.active-directory.bind-dn") { "cn=svc,ou=service,$BASE" }
            registry.add("app.auth.active-directory.bind-password") { "svc-secret" }
            registry.add("app.auth.active-directory.user-search-filter") { "(uid={0})" }
            registry.add("app.auth.active-directory.nested-groups") { "false" }
            registry.add("app.auth.active-directory.attributes.guid") { "entryUUID" }
            // By name and by DN: both forms are supported. A DN goes in by index — a plain
            // comma-separated value would be split into a list at its commas.
            registry.add("app.auth.active-directory.access.required-groups") { "App Users" }
            registry.add("app.auth.active-directory.access.admin-groups[0]") { ADMINS_GROUP }
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var refreshTokenService: RefreshTokenService
    @Autowired private lateinit var directoryClient: DirectoryClient
    @Autowired private lateinit var rateLimiter: RateLimiter
    @Autowired private lateinit var jwtDecoder: JwtDecoder
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun resetDirectory() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        server.clear()
        server.add("dn: $BASE", "objectClass: domain", "dc: corp")
        listOf("users", "groups", "service").forEach {
            server.add("dn: ou=$it,$BASE", "objectClass: organizationalUnit", "ou: $it")
        }
        server.add("dn: cn=svc,ou=service,$BASE", "objectClass: person", "cn: svc", "sn: svc", "userPassword: svc-secret")
        addUser("ivanov", "Ivan Ivanov", groups = listOf(USERS_GROUP))
        addUser("petrov", "Petr Petrov", groups = listOf(USERS_GROUP, ADMINS_GROUP))
        addUser("sidorov", "Sidor Sidorov", groups = emptyList())
        addUser("blocked", "Blocked User", groups = listOf(USERS_GROUP), accountControl = DISABLED)
        listOf("ivanov", "petrov", "sidorov", "blocked", "nobody").forEach { rateLimiter.reset("ad-login:$it") }
    }

    private fun addUser(uid: String, name: String, groups: List<String>, accountControl: String = ENABLED) {
        server.add(
            *(listOf(
                "dn: uid=$uid,ou=users,$BASE",
                "objectClass: inetOrgPerson",
                "uid: $uid",
                "cn: $name",
                "sn: $name",
                "displayName: $name",
                "mail: $uid@corp.local",
                "userPassword: $uid-password",
                "userAccountControl: $accountControl"
            ) + groups.map { "memberOf: $it" }).toTypedArray()
        )
    }

    private fun modify(uid: String, attribute: String, vararg values: String) {
        server.modify("uid=$uid,ou=users,$BASE", Modification(ModificationType.REPLACE, attribute, *values))
    }

    private fun login(username: String, password: String) = mockMvc.perform(
        post("/api/v1/auth/active-directory/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("username" to username, "password" to password)))
    )

    private fun loginTokens(username: String): Pair<String, String> {
        val body = login(username, "$username-password").andExpect(status().isOk).andReturn().response.contentAsString
        val json = objectMapper.readTree(body)
        return json["accessToken"].asString() to json["refreshToken"].asString()
    }

    private fun refresh(refreshToken: String) = mockMvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken)))
    )

    private fun directoryUser(): User =
        userRepository.findAll().single { AuthProvider.ACTIVE_DIRECTORY in it.providers }

    @Test
    fun `first login creates a user linked by the directory id`() {
        val (accessToken, _) = loginTokens("ivanov")

        val user = directoryUser()
        val directoryId = server.getEntry("uid=ivanov,ou=users,$BASE").getAttributeValue("entryUUID")
        assertThat(user.providerIds[AuthProvider.ACTIVE_DIRECTORY]).isEqualTo(directoryId)
        assertThat(user.email).isEqualTo("ivanov@corp.local")
        assertThat(user.name).isEqualTo("Ivan Ivanov")
        assertThat(user.roles).containsExactly(Role.USER)
        assertThat(user.directoryGroups).containsExactly("App Users")

        val jwt = jwtDecoder.decode(accessToken)
        assertThat(jwt.getClaimAsStringList("groups")).containsExactly("App Users")
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER")
    }

    @Test
    fun `a second login finds the same user`() {
        loginTokens("ivanov")
        loginTokens("ivanov")
        assertThat(userRepository.findAll().filter { AuthProvider.ACTIVE_DIRECTORY in it.providers }).hasSize(1)
    }

    @Test
    fun `domain prefixed logins are accepted`() {
        login("CORP\\ivanov", "ivanov-password").andExpect(status().isOk)
    }

    @Test
    fun `members of an admin group get the ADMIN role`() {
        val (accessToken, _) = loginTokens("petrov")
        assertThat(directoryUser().roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
        assertThat(jwtDecoder.decode(accessToken).getClaimAsStringList("roles")).contains("ADMIN")
    }

    @Test
    fun `wrong password and unknown account get the same answer`() {
        val wrong = login("ivanov", "not-the-password")
            .andExpect(status().isUnauthorized).andReturn().response.contentAsString
        val unknown = login("nobody", "whatever")
            .andExpect(status().isUnauthorized).andReturn().response.contentAsString
        assertThat(objectMapper.readTree(wrong)["message"]).isEqualTo(objectMapper.readTree(unknown)["message"])
    }

    @Test
    fun `an empty password never reaches an unauthenticated bind`() {
        // A simple bind with an empty password is "unauthenticated" and succeeds on most servers.
        assertThat(directoryClient.authenticate("ivanov", "")).isNull()
    }

    @Test
    fun `accounts outside the required groups cannot sign in`() {
        login("sidorov", "sidorov-password").andExpect(status().isUnauthorized)
        assertThat(userRepository.findAll()).isEmpty()
    }

    @Test
    fun `disabled accounts cannot sign in`() {
        login("blocked", "blocked-password").andExpect(status().isUnauthorized)
    }

    @Test
    fun `wrong passwords are limited before the domain lockout policy trips`() {
        repeat(3) { login("ivanov", "wrong-$it").andExpect(status().isUnauthorized) }
        login("ivanov", "ivanov-password")
            .andExpect(status().isTooManyRequests)
            .andExpect { assertThat(it.response.getHeader("Retry-After")).isNotNull() }
    }

    @Test
    fun `an email held by another account is a conflict, not a takeover`() {
        userRepository.save(User(email = "ivanov@corp.local").also { it.linkProvider(AuthProvider.LOCAL) })
        login("ivanov", "ivanov-password").andExpect(status().isConflict)
    }

    @Test
    fun `refresh syncs roles and groups from the directory`() {
        val (_, refreshToken) = loginTokens("ivanov")
        modify("ivanov", "memberOf", USERS_GROUP, ADMINS_GROUP)

        val body = refresh(refreshToken).andExpect(status().isOk).andReturn().response.contentAsString

        val accessToken = objectMapper.readTree(body)["accessToken"].asString()
        assertThat(jwtDecoder.decode(accessToken).getClaimAsStringList("roles")).contains("ADMIN")
        assertThat(jwtDecoder.decode(accessToken).getClaimAsStringList("groups"))
            .containsExactlyInAnyOrder("App Users", "App Admins")
    }

    @Test
    fun `a disabled account loses every session at the next refresh`() {
        val (_, first) = loginTokens("ivanov")
        val (_, second) = loginTokens("ivanov")
        modify("ivanov", "userAccountControl", DISABLED)

        refresh(first).andExpect(status().isUnauthorized)

        assertThat(refreshTokenRepository.findAll()).isEmpty()
        refresh(second).andExpect(status().isUnauthorized)
    }

    @Test
    fun `an account removed from the required groups loses its sessions`() {
        val (_, refreshToken) = loginTokens("ivanov")
        modify("ivanov", "memberOf", ADMINS_GROUP)

        refresh(refreshToken).andExpect(status().isUnauthorized)
        assertThat(refreshTokenRepository.findAll()).isEmpty()
    }

    @Test
    fun `a deleted account loses its sessions`() {
        val (_, refreshToken) = loginTokens("ivanov")
        server.delete("uid=ivanov,ou=users,$BASE")

        refresh(refreshToken).andExpect(status().isUnauthorized)
        assertThat(refreshTokenRepository.findAll()).isEmpty()
    }

    @Test
    fun `a directory outage refuses the refresh without spending the token`() {
        val (_, refreshToken) = loginTokens("ivanov")
        server.shutDown(true)
        try {
            refresh(refreshToken)
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.message").value("Directory is unavailable"))
        } finally {
            server.startListening()
        }
        refresh(refreshToken).andExpect(status().isOk)
    }

    @Test
    fun `local users refresh without asking the directory`() {
        server.shutDown(true)
        try {
            val user = userRepository.save(User(email = "local@example.com").also { it.linkProvider(AuthProvider.LOCAL) })
            refresh(refreshTokenService.createToken(user)).andExpect(status().isOk)
        } finally {
            server.startListening()
        }
    }
}
