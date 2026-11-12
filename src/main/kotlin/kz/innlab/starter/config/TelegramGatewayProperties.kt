package kz.innlab.starter.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import kz.innlab.starter.authentication.service.VerificationCodeService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * Telegram Gateway API (https://core.telegram.org/gateway/api) as a phone OTP channel, named
 * `telegram` in `app.auth.otp.delivery-order`. Telegram delivers the starter's own code; it does
 * not generate or check it.
 *
 * ProductionSafetyConfig refuses to start under a production profile when [enabled] is set and
 * [token] is blank. Outside production a blank token makes every send fail, so delivery falls
 * through to the next channel.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.telegram-gateway")
data class TelegramGatewayProperties(
    val enabled: Boolean = false,
    /** Gateway API token from the Telegram Gateway account settings. A secret: keep it in the environment. */
    val token: String = "",
    /** Optional: a verified Telegram channel the message is sent from. Blank — not sent. */
    val senderUsername: String = "",
    /** Optional: URL for delivery reports. Blank — not sent. Reports are not verified or processed yet. */
    val callbackUrl: String = "",
    /**
     * Seconds Telegram keeps trying to deliver before giving up (and refunding the message). The
     * API accepts 30–3600; the upper bound here is the phone code lifetime, as a code delivered
     * later is already expired.
     */
    @field:Min(30)
    @field:Max(VerificationCodeService.PHONE_EXPIRY_MINUTES * 60)
    val ttlSeconds: Int = (VerificationCodeService.PHONE_EXPIRY_MINUTES * 60).toInt(),
    @field:NotBlank val baseUrl: String = "https://gatewayapi.telegram.org",
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10)
)
