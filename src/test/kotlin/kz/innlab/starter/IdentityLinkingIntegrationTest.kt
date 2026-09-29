package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.user.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class IdentityLinkingIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var refreshTokens: RefreshTokenRepository
    @Autowired lateinit var service: UserService

    @BeforeEach
    fun cleanUp() {
        refreshTokens.deleteAll()
        users.deleteAll()
    }

    @Test
    fun `Google subject cannot attach to existing admin by matching email`() {
        val admin = users.save(User("admin@example.com").apply { roles.add(Role.ADMIN) })

        assertThrows(IllegalStateException::class.java) {
            service.findOrCreateGoogleUser("attacker-sub", admin.email, null, null)
        }

        val unchanged = users.findById(admin.id).orElseThrow()
        assertEquals(null, unchanged.providerIds[AuthProvider.GOOGLE])
        assertEquals(1L, users.count())
    }

    @Test
    fun `Google returning subject resolves original account despite email change`() {
        val original = users.save(User("first@example.com").apply {
            linkProvider(AuthProvider.GOOGLE, "original-sub")
        })
        assertEquals(original.id, service.findOrCreateGoogleUser("original-sub", "new@example.com", null, null).id)
        assertEquals(1L, users.count())
    }

    @Test
    fun `Apple subject cannot attach to existing admin by matching email`() {
        val admin = users.save(User("apple-admin@example.com").apply { roles.add(Role.ADMIN) })

        assertThrows(IllegalStateException::class.java) {
            service.findOrCreateAppleUser("attacker-apple-sub", admin.email, null)
        }

        val unchanged = users.findById(admin.id).orElseThrow()
        assertEquals(null, unchanged.providerIds[AuthProvider.APPLE])
        assertEquals(1L, users.count())
    }
}
