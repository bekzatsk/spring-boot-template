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
@Table(name = "notification_history", schema = "auth")
class NotificationHistory(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: NotificationType,

    @Column(nullable = false)
    val recipient: String,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val body: String
) : BaseEntity() {

    var data: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: NotificationStatus = NotificationStatus.PENDING

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
