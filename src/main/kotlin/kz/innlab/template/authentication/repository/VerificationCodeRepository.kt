package kz.innlab.template.authentication.repository

import kz.innlab.template.authentication.model.VerificationCode
import kz.innlab.template.authentication.model.VerificationPurpose
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
