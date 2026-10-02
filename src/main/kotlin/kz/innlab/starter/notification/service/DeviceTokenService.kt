package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.DeviceToken
import kz.innlab.starter.notification.model.Platform
import kz.innlab.starter.notification.repository.DeviceTokenRepository
import org.slf4j.LoggerFactory
import kz.innlab.starter.config.DeviceTokenProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val deviceTokenProperties: DeviceTokenProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceTokenService::class.java)
    }

    @Transactional
    fun register(userId: UUID, platform: Platform, fcmToken: String, deviceId: String): DeviceToken {
        val existing = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)

        // A token is one app install; whoever registers it last owns it. Leaving the previous
        // registration in place would let its user keep pushing to this device.
        val previousHolder = deviceTokenRepository.findByFcmToken(fcmToken)
        if (previousHolder != null && previousHolder.id != existing?.id) {
            deviceTokenRepository.delete(previousHolder)
            // Hibernate flushes inserts before deletes; without this the unique index rejects the save.
            deviceTokenRepository.flush()
        }

        if (existing != null) {
            existing.fcmToken = fcmToken
            existing.platform = platform
            existing.updatedAt = Instant.now()
            return deviceTokenRepository.save(existing)
        }

        val count = deviceTokenRepository.countByUserId(userId)
        if (count >= deviceTokenProperties.maxPerUser) {
            throw IllegalStateException("Maximum device tokens (${deviceTokenProperties.maxPerUser}) reached for user")
        }

        val token = DeviceToken(userId, platform, fcmToken, deviceId)
        return deviceTokenRepository.save(token)
    }

    @Transactional
    fun deleteByDeviceId(userId: UUID, deviceId: String) {
        val existing = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
        if (existing != null) {
            deviceTokenRepository.delete(existing)
        }
    }

    @Transactional
    fun deleteAllByUser(userId: UUID) {
        deviceTokenRepository.deleteByUserId(userId)
    }

    fun listByUser(userId: UUID): List<DeviceToken> {
        return deviceTokenRepository.findByUserId(userId)
    }

    @Transactional
    fun cleanupStaleTokens(staleTokens: List<String>) {
        if (staleTokens.isNotEmpty()) {
            deviceTokenRepository.deleteAllByFcmTokenIn(staleTokens)
            logger.info("Cleaned up {} stale FCM tokens", staleTokens.size)
        }
    }
}
