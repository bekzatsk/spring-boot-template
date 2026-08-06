package kz.innlab.starter.authentication.dto

import java.util.UUID

/** Handle for a pending verification: pass it back to the matching verify endpoint. */
data class VerificationIdResponse(
    val verificationId: UUID
)
