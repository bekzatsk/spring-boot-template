package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank

data class ChangePhoneRequest(
    @field:NotBlank val phone: String
)
