package kz.innlab.starter.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class AdminUpdateEmailRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)
