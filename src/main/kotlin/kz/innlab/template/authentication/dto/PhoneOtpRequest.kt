package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank

data class PhoneOtpRequest(
    @field:NotBlank(message = "Phone number is required")
    val phone: String
)
