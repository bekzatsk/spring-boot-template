package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank val newPassword: String
)
