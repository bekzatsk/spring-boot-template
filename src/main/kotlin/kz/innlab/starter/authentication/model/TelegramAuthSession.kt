package kz.innlab.starter.authentication.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kz.innlab.starter.shared.model.BaseEntity
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "telegram_auth_sessions", schema = "auth")
class TelegramAuthSession(
    @Column(name = "session_id", nullable = false, unique = true)
    val sessionId: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TelegramSessionStatus = TelegramSessionStatus.PENDING

    @Column(name = "code_hash")
    var codeHash: String? = null

    @Column(nullable = false)
    var attempts: Int = 0

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 3

    @Column(name = "telegram_user_id")
    var telegramUserId: Long? = null

    @Column(name = "telegram_username")
    var telegramUsername: String? = null

    @Column(name = "telegram_chat_id")
    var telegramChatId: Long? = null

    @Column(name = "ip_address")
    var ipAddress: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TelegramAuthSession) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
