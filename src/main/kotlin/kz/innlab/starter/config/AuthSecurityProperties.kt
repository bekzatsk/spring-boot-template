package kz.innlab.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.security")
data class AuthSecurityProperties(
    val enabled: Boolean = true,
    val publicPaths: List<String> = emptyList(),
    val requiredAction: RequiredActionEnforcement = RequiredActionEnforcement()
)

data class RequiredActionEnforcement(
    val enabled: Boolean = true,
    val allowedPaths: List<String> = listOf(
        "/api/v1/users/me",
        "/api/v1/users/me/change-password",
        "/api/v1/auth/refresh",
        "/api/v1/auth/revoke"
    )
)
