package kz.innlab.template.authentication.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sms_verifications")
class SmsVerification(
    @Column(name = "phone", nullable = false)
    val phone: String,

    @Column(name = "code_hash", nullable = false)
    val codeHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null

    @Column(nullable = false)
    var used: Boolean = false

    @Column(nullable = false)
    var attempts: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmsVerification) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
