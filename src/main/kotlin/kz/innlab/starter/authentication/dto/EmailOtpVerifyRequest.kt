package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class EmailOtpVerifyRequest(
    @field:NotNull(message = "Verification ID is required")
    val verificationId: UUID,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Verification code is required")
    @field:Pattern(regexp = "(?:\\d{4}|\\d{6})", message = "Code must be 4 or 6 digits")
    val code: String
)
