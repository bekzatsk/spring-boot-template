package kz.innlab.starter.authentication.dto

import java.time.Instant
import java.util.UUID

/**
 * A freshly issued one-time code. [code] is the plaintext value to deliver — it is never
 * persisted, only its hash is. The resend fields carry the rate-limit window so callers
 * can tell the client when a new code may be requested.
 */
data class IssuedCode(
    val verificationId: UUID,
    val code: String,
    val resendAvailableAt: Instant,
    val retryAfterSeconds: Long
)
