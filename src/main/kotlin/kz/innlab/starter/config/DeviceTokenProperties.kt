package kz.innlab.starter.config

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** Caps how many push device tokens one user may register. */
@Validated
@ConfigurationProperties(prefix = "app.notification.token")
data class DeviceTokenProperties(
    @field:Positive val maxPerUser: Int = 5
)
