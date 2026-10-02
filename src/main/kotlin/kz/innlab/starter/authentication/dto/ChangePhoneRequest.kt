package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank
import java.util.UUID

/** Same proof of ownership as [ChangeEmailRequest]. */
data class ChangePhoneRequest(
    @field:NotBlank val phone: String,
    val currentPassword: String? = null,
    val reauthVerificationId: UUID? = null,
    val reauthCode: String? = null
)
