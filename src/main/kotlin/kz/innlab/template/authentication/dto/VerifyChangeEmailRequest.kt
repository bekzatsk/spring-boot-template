package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class VerifyChangeEmailRequest(
    @field:NotNull val verificationId: UUID,
    @field:NotBlank val code: String
)
