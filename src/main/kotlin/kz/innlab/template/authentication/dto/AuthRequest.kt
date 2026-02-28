package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank

data class AuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String = ""
)
