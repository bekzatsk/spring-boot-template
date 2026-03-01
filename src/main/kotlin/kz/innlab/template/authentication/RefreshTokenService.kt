package kz.innlab.template.authentication

import kz.innlab.template.authentication.exception.TokenGracePeriodException
import kz.innlab.template.authentication.model.RefreshToken
import kz.innlab.template.authentication.repository.RefreshTokenRepository
import kz.innlab.template.user.model.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${app.auth.refresh-token.expiry-days:30}")
    private val expiryDays: Long
) {
    private val secureRandom = SecureRandom()
    private val graceWindowSeconds = 10L

    /** Generate a raw opaque token and persist its hash. Returns the raw token (sent to client). */
    @Transactional
    fun createToken(user: User): String {
        val rawToken = generateRawToken()
        val tokenHash = hashToken(rawToken)
        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenHash = tokenHash,
                expiresAt = Instant.now().plus(expiryDays, ChronoUnit.DAYS)
            )
        )
        return rawToken
    }

    /**
     * Rotate a refresh token. Returns a pair (user, newRawRefreshToken).
     * Handles grace window for concurrent mobile retries (TOKN-04) — returns 409 Conflict via TokenGracePeriodException.
     * Triggers reuse detection and full revocation if token is replayed outside grace window (TOKN-03).
     */
    @Transactional
    fun rotate(rawToken: String): Pair<User, String> {
        val hash = hashToken(rawToken)
        val stored = refreshTokenRepository.findByTokenHash(hash)
            ?: throw BadCredentialsException("Invalid refresh token")

        // Check expiry before revoke state (avoids treating expired tokens as reuse)
        if (stored.expiresAt.isBefore(Instant.now())) {
            throw BadCredentialsException("Refresh token expired")
        }

        if (stored.revoked) {
            val usedAt = stored.usedAt
            val replacedBy = stored.replacedByTokenHash
            if (usedAt != null && replacedBy != null &&
                Instant.now().isBefore(usedAt.plusSeconds(graceWindowSeconds))) {
                // Within grace window: concurrent mobile retry — signal client to retry with new token
                // Raw token is not stored (TOKN-02), so we cannot return the replacement.
                // Return 409 Conflict so the mobile client retries with the token it already received.
                throw TokenGracePeriodException("Token already rotated, retry with new token")
            }
            // Outside grace window (or no usedAt): reuse detected — revoke entire token family
            refreshTokenRepository.deleteAllByUser(stored.user)
            throw BadCredentialsException("Refresh token reuse detected")
        }

        // Normal rotation: issue new token, mark old as used
        val newRawToken = generateRawToken()
        val newHash = hashToken(newRawToken)
        refreshTokenRepository.save(
            RefreshToken(
                user = stored.user,
                tokenHash = newHash,
                expiresAt = Instant.now().plus(expiryDays, ChronoUnit.DAYS)
            )
        )
        stored.revoked = true
        stored.usedAt = Instant.now()
        stored.replacedByTokenHash = newHash
        refreshTokenRepository.save(stored)

        return Pair(stored.user, newRawToken)
    }

    /** Revoke a refresh token (logout). Idempotent — silently succeeds if token not found. */
    @Transactional
    fun revoke(rawToken: String) {
        val hash = hashToken(rawToken)
        val stored = refreshTokenRepository.findByTokenHash(hash) ?: return
        stored.revoked = true
        refreshTokenRepository.save(stored)
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(
            digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        )
    }
}
