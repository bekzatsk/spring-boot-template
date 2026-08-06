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
    @Query("UPDATE VerificationCode vc SET vc.attempts = vc.attempts + 1 WHERE vc.id = :id")
    fun incrementAttempts(@Param("id") id: UUID): Int

    @Modifying
    @Query("UPDATE VerificationCode vc SET vc.used = true WHERE vc.id = :id")
    fun markUsed(@Param("id") id: UUID): Int

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
