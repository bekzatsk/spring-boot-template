package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank val newPassword: String
)
