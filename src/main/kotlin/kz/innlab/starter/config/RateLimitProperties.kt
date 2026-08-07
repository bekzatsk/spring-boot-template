package kz.innlab.starter.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Attempt limits for the credential-checking endpoints. Set `enabled: false` only if the limit
 * is enforced in front of the application (API gateway, WAF).
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    /** Password check on /auth/local/login, keyed by email and by client address. */
    @field:Valid val login: Rule = Rule(maxAttempts = 10, windowSeconds = 300),
    /** Current-password check on /users/me/change-password, keyed by user id. */
    @field:Valid val changePassword: Rule = Rule(maxAttempts = 5, windowSeconds = 300)
) {
    data class Rule(
        @field:Positive val maxAttempts: Int = 10,
        @field:Positive val windowSeconds: Long = 300
    )
}
