package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank

data class ForgotPasswordRequest(
    @field:NotBlank val email: String
)
