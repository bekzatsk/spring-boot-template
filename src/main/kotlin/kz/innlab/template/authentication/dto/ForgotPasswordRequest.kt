package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank

data class ForgotPasswordRequest(
    @field:NotBlank val email: String
)
