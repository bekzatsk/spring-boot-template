package kz.innlab.starter.authentication.repository

import kz.innlab.starter.authentication.model.VerificationCode
import kz.innlab.starter.authentication.model.VerificationPurpose
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface VerificationCodeRepository : JpaRepository<VerificationCode, UUID> {

    fun existsByIdentifierAndPurposeAndCreatedAtAfter(
        identifier: String,
        purpose: VerificationPurpose,
        since: Instant
    ): Boolean

    /**
     * Atomic increment, deliberately not a read-modify-write on the entity: the counter must
     * survive the rollback of the caller's transaction (a failed verification throws) and must
     * not lose concurrent increments.
     */
    @Modifying
    @Query("""
        UPDATE VerificationCode vc SET vc.attempts = vc.attempts + 1
        WHERE vc.id = :id AND vc.used = false AND vc.expiresAt > :now
          AND vc.attempts < :maxAttempts
    """)
    fun incrementAttemptsIfActive(
        @Param("id") id: UUID,
        @Param("now") now: Instant,
        @Param("maxAttempts") maxAttempts: Int
    ): Int

    /**
     * Burns the code only if it still has [codeHash] — the hash the caller checked the code
     * against. A code replaced in between (replaceCodeIfActive) no longer matches, so a check that
     * started before the replacement cannot redeem the old code.
     */
    @Modifying
    @Query("""
        UPDATE VerificationCode vc SET vc.used = true
        WHERE vc.id = :id AND vc.codeHash = :codeHash AND vc.used = false AND vc.expiresAt > :now
          AND vc.attempts <= :maxAttempts
    """)
    fun markUsedIfActive(
        @Param("id") id: UUID,
        @Param("codeHash") codeHash: String,
        @Param("now") now: Instant,
        @Param("maxAttempts") maxAttempts: Int
    ): Int

    fun findByGatewayRequestId(gatewayRequestId: String): VerificationCode?

    @Modifying
    @Query("UPDATE VerificationCode vc SET vc.gatewayRequestId = :requestId WHERE vc.id = :id")
    fun setGatewayRequestId(@Param("id") id: UUID, @Param("requestId") requestId: String): Int

    /**
     * Swaps the code of a live verification for a new one, keeping its id (the client holds it),
     * expiry and attempt count. Matching on the request id makes it a compare-and-set: a repeated
     * delivery report finds the id already cleared and changes nothing.
     */
    @Modifying
    @Query("""
        UPDATE VerificationCode vc SET vc.codeHash = :codeHash, vc.gatewayRequestId = null
        WHERE vc.id = :id AND vc.gatewayRequestId = :requestId AND vc.used = false
          AND vc.expiresAt > :now AND vc.attempts < :maxAttempts
    """)
    fun replaceCodeIfActive(
        @Param("id") id: UUID,
        @Param("requestId") requestId: String,
        @Param("codeHash") codeHash: String,
        @Param("now") now: Instant,
        @Param("maxAttempts") maxAttempts: Int
    ): Int

    @Modifying
    @Transactional
    @Query("DELETE FROM VerificationCode vc WHERE vc.expiresAt < :cutoff OR vc.used = true")
    fun deleteExpiredOrUsed(@Param("cutoff") cutoff: Instant)

    @Modifying
    @Transactional
    @Query("DELETE FROM VerificationCode vc WHERE vc.identifier = :identifier AND vc.purpose = :purpose")
    fun deleteAllByIdentifierAndPurpose(
        @Param("identifier") identifier: String,
        @Param("purpose") purpose: VerificationPurpose
    )
}
