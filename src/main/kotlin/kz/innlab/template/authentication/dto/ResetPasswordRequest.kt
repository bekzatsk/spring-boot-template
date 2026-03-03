package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class ResetPasswordRequest(
    @field:NotNull val verificationId: UUID,
    @field:NotBlank val email: String,
    @field:NotBlank val code: String,
    @field:NotBlank val newPassword: String
)
