package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    val newPassword: String
)
