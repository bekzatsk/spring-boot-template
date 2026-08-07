package kz.innlab.starter.config

import jakarta.validation.Valid
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * One-time code settings. The two dev-code overrides are separate because they gate different
 * channels; ProductionSafetyConfig refuses to start when either is set under the prod profile.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
data class VerificationProperties(
    @field:Valid val verification: Channel = Channel(),
    @field:Valid val sms: Channel = Channel()
) {
    data class Channel(
        /** Fixed code for local development. Must be blank in production. */
        val devCode: String = ""
    )
}
