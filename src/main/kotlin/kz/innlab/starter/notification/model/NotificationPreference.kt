package kz.innlab.starter.notification.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kz.innlab.starter.shared.model.BaseEntity
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification_preferences", schema = "auth")
class NotificationPreference(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var channel: NotificationChannel,

    @Column(nullable = false)
    var enabled: Boolean = true
) : BaseEntity() {

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
