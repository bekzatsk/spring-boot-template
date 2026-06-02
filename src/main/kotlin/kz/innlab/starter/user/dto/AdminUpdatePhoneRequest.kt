package kz.innlab.starter.user.dto

import jakarta.validation.constraints.NotBlank

data class AdminUpdatePhoneRequest(
    @field:NotBlank(message = "Phone is required")
    val phone: String
)
