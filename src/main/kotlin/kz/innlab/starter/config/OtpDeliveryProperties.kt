package kz.innlab.starter.config

import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Phone OTP delivery. [deliveryOrder] lists channel names — `telegram`, `whatsapp`, `sms`, or the
 * name of an application's own `OtpDeliveryChannel` — tried in turn until one accepts the code.
 * A name without a configured channel is skipped; an order with no configured channel at all
 * fails at startup (OtpDeliveryService).
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.otp")
data class OtpDeliveryProperties(
    /** Default keeps the behaviour from before the order was configurable. */
    @field:NotEmpty val deliveryOrder: List<String> = listOf("whatsapp", "sms")
)
