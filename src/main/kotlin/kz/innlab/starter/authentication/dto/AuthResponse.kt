package kz.innlab.starter.authentication.dto

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val requiredActions: List<String> = emptyList()
)
