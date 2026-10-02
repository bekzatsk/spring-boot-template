package kz.innlab.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.security")
data class AuthSecurityProperties(
    val enabled: Boolean = true,
    val publicPaths: List<String> = emptyList(),
    /** Serve Swagger UI and the OpenAPI spec without authentication. Off by default: the spec maps every endpoint. */
    val publicApiDocs: Boolean = false,
    /**
     * How recent the admin's login must be (seconds, from the token's auth_time) for password,
     * email, phone, role changes and deletion of a user. A stolen admin token older than this
     * cannot take over accounts — its own included.
     */
    val adminFreshLoginSeconds: Long = 900,
    val requiredAction: RequiredActionEnforcement = RequiredActionEnforcement()
)

data class RequiredActionEnforcement(
    val enabled: Boolean = true,
    val allowedPaths: List<String> = listOf(
        "/api/v1/users/me",
        "/api/v1/users/me/change-password",
        "/api/v1/auth/refresh",
        "/api/v1/auth/revoke",
        "/api/v1/auth/logout",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/verify-email/resend"
    )
)
