package kz.innlab.starter.authentication.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ActiveDirectoryLoginRequest(
    /** `ivanov`, `ivanov@corp.local` or `CORP\ivanov`. */
    @field:NotBlank(message = "Username is required")
    @field:Size(max = 256, message = "Username is too long")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(max = 512, message = "Password is too long")
    val password: String
)
