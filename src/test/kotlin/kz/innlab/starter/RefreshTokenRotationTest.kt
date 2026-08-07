package kz.innlab.starter

import kz.innlab.starter.authentication.exception.TokenGracePeriodException
import kz.innlab.starter.authentication.model.RefreshToken
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.BadCredentialsException
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/**
 * Refresh-token rotation is the most intricate logic in the starter and was only exercised
 * end-to-end through HTTP, which never reaches the interesting states: a replay just inside the
 * grace window is a legitimate mobile retry, the same replay a moment later is a stolen token
 * and must burn the whole family.
 */
@SpringBootTest
class RefreshTokenRotationTest {

    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        user = userRepository.save(
            User(email = "rotation@example.com").also { it.linkProvider(AuthProvider.LOCAL) }
        )
    }

    /** Rewinds the consumed token's usedAt so the replay lands outside the 10s grace window. */
    private fun ageTheConsumedToken() {
        val consumed = refreshTokenRepository.findAll().first { it.revoked }
        consumed.usedAt = Instant.now().minusSeconds(600)
        refreshTokenRepository.save(consumed)
    }

    @Test
    fun `rotation issues a new token and consumes the old one`() {
        val original = refreshTokenService.createToken(user)

        val (returnedUser, rotated) = refreshTokenService.rotate(original)

        assertThat(returnedUser.id).isEqualTo(user.id)
        assertThat(rotated).isNotEqualTo(original)

        val consumed = refreshTokenRepository.findAll().first { it.revoked }
        assertThat(consumed.usedAt).isNotNull()
        assertThat(consumed.replacedByTokenHash)
            .describedAs("the replacement must be recorded to tell a retry from a replay")
            .isNotNull()
    }

    @Test
    fun `the rotated token can itself be rotated`() {
        val first = refreshTokenService.createToken(user)
        val (_, second) = refreshTokenService.rotate(first)

        val (_, third) = refreshTokenService.rotate(second)

        assertThat(third).isNotEqualTo(second)
    }

    @Test
    fun `an unknown token is rejected`() {
        assertThrows<BadCredentialsException> { refreshTokenService.rotate("never-issued") }
    }

    /**
     * Mirrors RefreshTokenService's hashing so an already-expired token can be planted.
     * expiresAt is immutable on the entity, so an issued token cannot be aged in place.
     */
    private fun hashOf(rawToken: String): String = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
    )

    @Test
    fun `an expired token is rejected before it can look like reuse`() {
        val token = "expired-raw-token"
        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenHash = hashOf(token),
                expiresAt = Instant.now().minusSeconds(60)
            )
        )
        refreshTokenService.createToken(user)

        assertThrows<BadCredentialsException> { refreshTokenService.rotate(token) }

        // Expiry is checked first on purpose: an expired token is not evidence of theft,
        // so the user's other sessions must survive.
        assertThat(refreshTokenRepository.findAll()).isNotEmpty()
    }

    @Test
    fun `replaying inside the grace window signals a retry instead of revoking`() {
        val original = refreshTokenService.createToken(user)
        refreshTokenService.rotate(original)

        // A mobile client that fired two refreshes at once gets a conflict, not a lockout.
        assertThrows<TokenGracePeriodException> { refreshTokenService.rotate(original) }

        assertThat(refreshTokenRepository.findAll())
            .describedAs("a concurrent retry must not destroy the session")
            .isNotEmpty()
    }

    @Test
    fun `replaying after the grace window revokes the whole family`() {
        val original = refreshTokenService.createToken(user)
        refreshTokenService.rotate(original)
        ageTheConsumedToken()

        assertThrows<BadCredentialsException> { refreshTokenService.rotate(original) }

        assertThat(refreshTokenRepository.findAll())
            .describedAs("a replayed token means the chain leaked; every token must go")
            .isEmpty()
    }

    @Test
    fun `a token revoked by logout counts as reuse when replayed`() {
        val token = refreshTokenService.createToken(user)
        refreshTokenService.revoke(token)

        // revoke() leaves usedAt null, so this takes the reuse branch rather than the grace one.
        assertThrows<BadCredentialsException> { refreshTokenService.rotate(token) }

        assertThat(refreshTokenRepository.findAll()).isEmpty()
    }

    @Test
    fun `revoking a token that does not exist is silently accepted`() {
        refreshTokenService.revoke("never-issued")
    }

    @Test
    fun `one leaked token takes every session of that user with it`() {
        val leaked = refreshTokenService.createToken(user)
        refreshTokenService.createToken(user)
        refreshTokenService.createToken(user)
        refreshTokenService.rotate(leaked)
        ageTheConsumedToken()

        assertThrows<BadCredentialsException> { refreshTokenService.rotate(leaked) }

        assertThat(refreshTokenRepository.findAll())
            .describedAs("sessions on other devices are collateral, and that is the intent")
            .isEmpty()
    }
}
