package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank

data class ResendEmailVerificationRequest(
    @field:NotBlank val email: String
)
