package kz.innlab.starter.notification.dto

/**
 * Bounds for a push `data` map. The entry count alone did not bound it: a single value of many
 * megabytes was accepted and stored in notification_history on every call. FCM itself rejects
 * messages over 4 KB, so nothing legitimate is lost.
 */
internal object PushPayloadLimits {
    const val MAX_ENTRIES = 20
    const val MAX_KEY_LENGTH = 64
    const val MAX_VALUE_LENGTH = 1024
    const val MAX_BODY_LENGTH = 2000

    fun dataWithinLimits(data: Map<String, String>): Boolean =
        data.all { (key, value) -> key.length <= MAX_KEY_LENGTH && value.length <= MAX_VALUE_LENGTH }
}
