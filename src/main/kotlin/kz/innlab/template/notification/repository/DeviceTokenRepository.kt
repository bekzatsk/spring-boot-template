package kz.innlab.template.notification.repository

import kz.innlab.template.notification.model.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceTokenRepository : JpaRepository<DeviceToken, UUID> {

    fun findByUserId(userId: UUID): List<DeviceToken>

    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): DeviceToken?

    fun deleteByFcmToken(fcmToken: String)

    fun deleteAllByFcmTokenIn(fcmTokens: List<String>)

    fun deleteByUserId(userId: UUID)

    fun countByUserId(userId: UUID): Long
}
