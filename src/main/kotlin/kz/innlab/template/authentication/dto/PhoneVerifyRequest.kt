package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class PhoneVerifyRequest(
    @field:NotNull(message = "Verification ID is required")
    val verificationId: UUID,

    @field:NotBlank(message = "Phone number is required")
    val phone: String,

    @field:NotBlank(message = "Verification code is required")
    @field:Size(min = 6, max = 6, message = "Code must be 6 digits")
    val code: String
)
