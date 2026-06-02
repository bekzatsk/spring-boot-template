package kz.innlab.starter.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AdminUpdatePasswordRequest(
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    val newPassword: String,

    val temporary: Boolean = false
)
