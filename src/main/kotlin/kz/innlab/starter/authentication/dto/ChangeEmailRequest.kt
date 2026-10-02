package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID

/**
 * Starting an email change needs proof that the caller is the account owner, not just someone
 * holding its access token: [currentPassword], or a code from `/users/me/reauth/request`.
 */
data class ChangeEmailRequest(
    @field:NotBlank @field:Email val newEmail: String,
    val currentPassword: String? = null,
    val reauthVerificationId: UUID? = null,
    val reauthCode: String? = null
)
