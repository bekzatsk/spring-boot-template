package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PhoneVerifyRequest(
    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String,

    @field:NotBlank(message = "Verification code is required")
    @field:Size(min = 6, max = 6, message = "Code must be 6 digits")
    val code: String
)
