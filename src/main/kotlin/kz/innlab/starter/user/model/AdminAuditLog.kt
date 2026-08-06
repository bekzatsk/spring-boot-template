package kz.innlab.starter.user.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kz.innlab.starter.shared.model.BaseEntity
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admin_audit_log", schema = "auth")
class AdminAuditLog(
    @Column(name = "admin_id", nullable = false)
    val adminId: UUID,

    @Column(name = "action", nullable = false, length = 100)
    val action: String,

    @Column(name = "target_id")
    val targetId: UUID? = null,

    @Column(name = "before_value", columnDefinition = "TEXT")
    val before: String? = null,

    @Column(name = "after_value", columnDefinition = "TEXT")
    val after: String? = null
) : BaseEntity() {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
}
