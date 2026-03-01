package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank

data class AppleAuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String = "",

    // Name fields: only present on first sign-in (iOS sends from ASAuthorizationAppleIDCredential.fullName)
    // Absent on subsequent sign-ins — backend must handle null gracefully
    val givenName: String? = null,
    val familyName: String? = null
)
