package kz.innlab.starter.authentication.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kz.innlab.starter.shared.model.BaseEntity
import kz.innlab.starter.user.model.User
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
) : BaseEntity() {

    @Column(nullable = false)
    var revoked: Boolean = false

    @Column(name = "used_at")
    var usedAt: Instant? = null

    @Column(name = "replaced_by_token_hash")
    var replacedByTokenHash: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefreshToken) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
