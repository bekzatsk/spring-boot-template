package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class ResetPasswordRequest(
    @field:NotNull val verificationId: UUID,
    @field:NotBlank val email: String,
    @field:NotBlank val code: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    val newPassword: String
)
