package kz.innlab.starter.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** Identity of tokens issued by this application and accepted by its resource server. */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
data class JwtIdentityProperties(
    @field:NotBlank val issuer: String = "template-app",
    @field:NotBlank val audience: String = "template-app"
)
