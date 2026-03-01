package kz.innlab.template.authentication.dto

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)
