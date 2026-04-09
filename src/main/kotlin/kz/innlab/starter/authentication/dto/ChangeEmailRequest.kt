package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class ChangeEmailRequest(
    @field:NotBlank @field:Email val newEmail: String
)
