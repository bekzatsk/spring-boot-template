package kz.innlab.starter.notification.dto

import kz.innlab.starter.notification.model.DeviceToken
import java.time.Instant
import java.util.UUID

data class DeviceTokenResponse(
    val id: UUID,
    val platform: String,
    val fcmToken: String,
    val deviceId: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(token: DeviceToken): DeviceTokenResponse = DeviceTokenResponse(
            id = token.id,
            platform = token.platform.name,
            fcmToken = token.fcmToken,
            deviceId = token.deviceId,
            createdAt = token.createdAt,
            updatedAt = token.updatedAt
        )
    }
}
