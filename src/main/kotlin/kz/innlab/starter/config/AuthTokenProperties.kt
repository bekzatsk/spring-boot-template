package kz.innlab.starter.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** Lifetimes of the tokens the starter issues. Validated at startup so a bad value fails fast. */
@Validated
@ConfigurationProperties(prefix = "app.auth")
data class AuthTokenProperties(
    @field:Valid val accessToken: AccessToken = AccessToken(),
    @field:Valid val refreshToken: RefreshToken = RefreshToken(),
    val registration: Registration = Registration(),
    val emailVerification: EmailVerification = EmailVerification()
) {
    data class AccessToken(
        @field:Positive val expiryMinutes: Long = 15
    )

    data class RefreshToken(
        @field:Positive val expiryDays: Long = 30
    )

    data class Registration(
        val enabled: Boolean = true
    )

    data class EmailVerification(
        val enabled: Boolean = false
    )
}
