package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.DeviceToken
import kz.innlab.starter.notification.model.Platform
import kz.innlab.starter.notification.repository.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
    @Value("\${app.notification.token.max-per-user:5}")
    private val maxTokensPerUser: Int
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceTokenService::class.java)
    }

    @Transactional
    fun register(userId: UUID, platform: Platform, fcmToken: String, deviceId: String): DeviceToken {
        val existing = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
        if (existing != null) {
            existing.fcmToken = fcmToken
            existing.platform = platform
            existing.updatedAt = Instant.now()
            return deviceTokenRepository.save(existing)
        }

        val count = deviceTokenRepository.countByUserId(userId)
        if (count >= maxTokensPerUser) {
            throw IllegalStateException("Maximum device tokens ($maxTokensPerUser) reached for user")
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
