package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class EmailOtpRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)
