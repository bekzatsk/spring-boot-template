package kz.innlab.template.authentication.repository

import kz.innlab.template.authentication.model.SmsVerification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface SmsVerificationRepository : JpaRepository<SmsVerification, UUID> {

    @Query("""
        SELECT sv FROM SmsVerification sv
        WHERE sv.phone = :phone
          AND sv.used = false
          AND sv.expiresAt > :now
          AND sv.attempts < 3
    """)
    fun findActiveByPhone(
        @Param("phone") phone: String,
        @Param("now") now: Instant
    ): SmsVerification?

    @Modifying
    @Transactional
    @Query("DELETE FROM SmsVerification sv WHERE sv.phone = :phone")
    fun deleteAllByPhone(@Param("phone") phone: String)

    @Modifying
    @Transactional
    @Query("DELETE FROM SmsVerification sv WHERE sv.expiresAt < :cutoff OR sv.used = true")
    fun deleteExpiredOrUsed(@Param("cutoff") cutoff: Instant)

    fun existsByPhoneAndCreatedAtAfter(phone: String, since: Instant): Boolean
}
