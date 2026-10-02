package kz.innlab.starter.user.service

import java.util.UUID

/**
 * Port for dropping every push-device registration of a user. Implemented by the notification
 * module ([kz.innlab.starter.notification.service.DeviceTokenService]) so the user module does not
 * depend on it — the same shape as [RefreshTokenRevoker].
 */
fun interface DeviceRegistrationRevoker {
    fun revokeAllFor(userId: UUID)
}
