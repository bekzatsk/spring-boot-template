package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank

data class ChangePhoneRequest(
    @field:NotBlank val phone: String
)
