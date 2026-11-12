package kz.innlab.starter.notification.dto

import kz.innlab.starter.notification.model.DeviceToken
import java.time.Instant
import java.util.UUID

data class DeviceTokenResponse(
    val id: UUID,
    val platform: String,
    val fcmToken: String,
    val deviceId: String,
    val tokenType: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(token: DeviceToken): DeviceTokenResponse = DeviceTokenResponse(
            id = token.id,
            platform = token.platform.name,
            fcmToken = token.fcmToken,
            deviceId = token.deviceId,
            tokenType = token.targetType.name,
            createdAt = token.createdAt,
            updatedAt = token.updatedAt
        )
    }
}
