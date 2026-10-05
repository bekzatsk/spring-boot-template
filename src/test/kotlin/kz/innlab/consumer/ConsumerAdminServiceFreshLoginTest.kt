package kz.innlab.consumer

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.shared.error.ForbiddenOperationException
import kz.innlab.starter.shared.error.RecentLoginRequiredException
import kz.innlab.starter.shared.security.FreshLoginGuard
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.user.service.AdminUserService
import kz.innlab.starter.user.service.UserService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/** A consumer's team endpoint that calls the starter's admin service directly. */
@RestController
class ConsumerTeamController(private val adminUserService: AdminUserService) {
    @PostMapping("/api/team/{id}/make-admin")
    fun makeAdmin(@PathVariable id: UUID): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication!!.name)
        adminUserService.updateRoles(adminId, id, setOf(Role.USER, Role.ADMIN))
        return ResponseEntity.noContent().build()
    }
}

/**
 * The recent-login rule lives in the services, so a consumer endpoint that calls them directly
 * cannot skip it — the hole reported against 0.1.5, where only the starter's controller checked.
 */
@SpringBootTest(classes = [ConsumerApplication::class])
@AutoConfigureMockMvc
class ConsumerAdminServiceFreshLoginTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var tokenService: TokenService
    @Autowired private lateinit var jwtDecoder: JwtDecoder
    @Autowired private lateinit var adminUserService: AdminUserService
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var freshLoginGuard: FreshLoginGuard
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var admin: User
    private lateinit var member: User

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        admin = userRepository.save(User(email = "lead@example.com").also {
            it.linkProvider(AuthProvider.LOCAL); it.roles.add(Role.ADMIN)
        })
        member = userRepository.save(User(email = "dev@example.com").also { it.linkProvider(AuthProvider.LOCAL) })
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun adminToken(loggedInAt: Instant) = tokenService.generateAccessToken(
        admin.id, setOf(Role.USER, Role.ADMIN), authTime = loggedInAt
    )

    private fun actAs(token: String) {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwtDecoder.decode(token), AuthorityUtils.createAuthorityList("ROLE_ADMIN"))
    }

    @Test
    fun `a consumer endpoint calling the service with a stale admin login gets 403`() {
        mockMvc.perform(
            post("/api/team/${member.id}/make-admin")
                .header("Authorization", "Bearer ${adminToken(Instant.now().minusSeconds(3600))}")
        ).andExpect(status().isForbidden)
        assert(Role.ADMIN !in userRepository.findById(member.id).orElseThrow().roles)
    }

    @Test
    fun `the same call with a recent login goes through`() {
        mockMvc.perform(
            post("/api/team/${member.id}/make-admin")
                .header("Authorization", "Bearer ${adminToken(Instant.now())}")
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `every handover service method refuses a stale login`() {
        actAs(adminToken(Instant.now().minusSeconds(3600)))
        val calls = listOf<() -> Unit>(
            { adminUserService.updateRoles(admin.id, member.id, setOf(Role.ADMIN)) },
            { adminUserService.updatePassword(admin.id, member.id, "NewPassword123", false) },
            { adminUserService.updateEmail(admin.id, member.id, "moved@example.com") },
            { adminUserService.updatePhone(admin.id, member.id, "+77001234567") },
            { adminUserService.deleteUser(admin.id, member.id) },
            { adminUserService.createUser(admin.id, "new@example.com", "Password123", null, setOf(Role.ADMIN), false) },
            { userService.createUserByAdmin("direct@example.com", "Password123", null, setOf(Role.ADMIN)) }
        )
        calls.forEach { call -> assertThatThrownBy { call() }.isInstanceOf(RecentLoginRequiredException::class.java) }
    }

    @Test
    fun `anonymous callers are refused and code with no caller is allowed`() {
        SecurityContextHolder.getContext().authentication = AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        )
        assertThatThrownBy { freshLoginGuard.requireFreshLogin() }.isInstanceOf(ForbiddenOperationException::class.java)

        SecurityContextHolder.clearContext()
        freshLoginGuard.requireFreshLogin()  // a scheduled job or migration: no user, no check
    }
}
