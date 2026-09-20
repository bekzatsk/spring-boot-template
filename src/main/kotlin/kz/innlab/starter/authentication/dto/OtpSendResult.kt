package kz.innlab.starter.authentication.dto

import java.time.Instant
import java.util.UUID

/**
 * Result of an OTP send request.
 * [resendAvailableAt] is the earliest instant a new code may be requested for the same identifier;
 * [retryAfterSeconds] is the cooldown window length in seconds (for client countdown timers).
 */
data class OtpSendResult(
    val verificationId: UUID,
    val resendAvailableAt: Instant,
    val retryAfterSeconds: Long
)
