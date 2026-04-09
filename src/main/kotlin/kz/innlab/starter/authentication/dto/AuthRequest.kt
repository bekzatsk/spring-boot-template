package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank

data class AuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String = "",
    val name: String? = null,
    val picture: String? = null
)
