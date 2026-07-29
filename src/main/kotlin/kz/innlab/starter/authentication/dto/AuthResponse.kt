package kz.innlab.starter.authentication.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val requiredActions: List<String> = emptyList(),
    /** Present only on register when email verification is pending — pass to POST /verify-email. */
    val verificationId: UUID? = null
)
