package kz.innlab.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** Google sign-in. `client-id` is read by GoogleAuthConfig. */
@Validated
@ConfigurationProperties(prefix = "app.auth.google")
data class GoogleAuthProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    /**
     * Sign a Google account in to an existing account with the same, Google-verified email instead
     * of answering 409. Off by default: it trusts Google's verification of the address as proof of
     * ownership of the account. It never applies to an account whose own email is unverified (its
     * registrant may not own the address) or to an administrator, which must link explicitly
     * through POST /api/v1/users/me/providers/google.
     */
    val linkByVerifiedEmail: Boolean = false
)
