package kz.innlab.starter.authentication.dto

import java.util.UUID

/** [channel] tells the client where the code went: `EMAIL` or `PHONE`. */
data class ReauthCodeResponse(
    val verificationId: UUID,
    val channel: String
)
