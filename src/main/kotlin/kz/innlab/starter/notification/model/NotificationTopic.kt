package kz.innlab.starter.notification.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kz.innlab.starter.shared.model.BaseEntity
import java.time.Instant

@Entity
@Table(name = "notification_topics")
class NotificationTopic(
    @Column(unique = true, nullable = false)
    var name: String
) : BaseEntity() {

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
