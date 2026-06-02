package kz.innlab.starter.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kz.innlab.starter.user.model.Role

data class AdminCreateUserRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    val password: String,

    val name: String? = null,

    val roles: Set<Role>? = null,

    val temporary: Boolean = false
)
