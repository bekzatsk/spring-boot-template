package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class VerifyEmailRequest(
    @field:NotNull val verificationId: UUID,
    @field:NotBlank val email: String,
    @field:NotBlank val code: String
)
