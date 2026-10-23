package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank

data class MicrosoftAuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String = ""
)
