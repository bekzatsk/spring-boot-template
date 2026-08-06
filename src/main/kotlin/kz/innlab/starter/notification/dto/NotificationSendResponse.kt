package kz.innlab.starter.notification.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

/**
 * Either the notification was queued ([notificationId]) or it was dropped because the user
 * disabled the PUSH channel ([skipped] + [reason]). Unset fields are omitted from the JSON,
 * so the wire format matches the ad-hoc maps this replaced.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NotificationSendResponse(
    val notificationId: UUID? = null,
    val skipped: Boolean? = null,
    val reason: String? = null
) {
    companion object {
        fun queued(notificationId: UUID) = NotificationSendResponse(notificationId = notificationId)

        fun skipped(reason: String) = NotificationSendResponse(skipped = true, reason = reason)
    }
}
