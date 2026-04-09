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
@Table(name = "device_tokens")
class DeviceToken(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var platform: Platform,

    @Column(name = "fcm_token", nullable = false)
    var fcmToken: String,

    @Column(name = "device_id", nullable = false)
    var deviceId: String
) : BaseEntity() {

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
