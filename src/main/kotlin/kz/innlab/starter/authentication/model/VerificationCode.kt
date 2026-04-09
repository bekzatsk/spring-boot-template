package kz.innlab.starter.authentication.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kz.innlab.starter.shared.model.BaseEntity
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "verification_codes")
class VerificationCode(
    @Column(name = "identifier", nullable = false)
    val identifier: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    val purpose: VerificationPurpose,

    @Column(name = "code_hash", nullable = false)
    val codeHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "new_value")
    val newValue: String? = null,

    @Column(name = "user_id")
    val userId: UUID? = null
) : BaseEntity() {

    @Column(nullable = false)
    var used: Boolean = false

    @Column(nullable = false)
    var attempts: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerificationCode) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
