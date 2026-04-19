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
@Table(name = "mail_history", schema = "auth")
class MailHistory(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "to_address", nullable = false)
    var toAddress: String,

    @Column(nullable = false)
    var subject: String
) : BaseEntity() {

    @Column(name = "text_body")
    var textBody: String? = null

    @Column(name = "html_body")
    var htmlBody: String? = null

    @Column(name = "has_attachments", nullable = false)
    var hasAttachments: Boolean = false

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MailStatus = MailStatus.PENDING

    @Column(nullable = false)
    var attempts: Int = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
