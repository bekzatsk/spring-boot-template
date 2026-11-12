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
 * [token] is blank, or when [callbackUrl] is set and is not `https://`. Outside production a blank
 * token makes every send fail, so delivery falls through to the next channel.
 *
 * Delivery reports: with [callbackUrl] set, the Gateway POSTs a signed RequestStatus to it;
 * TelegramGatewayCallbackController (`POST /api/v1/auth/telegram-gateway/callback`) checks the
 * signature with [token] and hands the report to TelegramGatewayReportService. With
 * [fallbackOnUndelivered], an `expired` delivery (Telegram gave up after [ttlSeconds]) replaces the
 * code with a new one sent through the channels after `telegram` in `app.auth.otp.delivery-order`.
 * The new code keeps the old one's expiry, so it has (code lifetime − ttl) left: the combination is
 * refused at startup unless [ttlSeconds] is below the phone code lifetime.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.telegram-gateway")
data class TelegramGatewayProperties(
    val enabled: Boolean = false,
    /** Gateway API token from the Telegram Gateway account settings. A secret: keep it in the environment. */
    val token: String = "",
    /** Optional: a verified Telegram channel the message is sent from. Blank — not sent. */
    val senderUsername: String = "",
    /**
     * Optional: URL the Gateway sends delivery reports to — the public address of
     * `/api/v1/auth/telegram-gateway/callback`. Blank — not sent, and no reports arrive.
     */
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
    val readTimeout: Duration = Duration.ofSeconds(10),
    /**
     * On an `expired` delivery report, issue a new code and send it through the channels after
     * `telegram` in `app.auth.otp.delivery-order`; the undelivered code stops working. Requires
     * [ttlSeconds] below the phone code lifetime.
     */
    val fallbackOnUndelivered: Boolean = false,
    /**
     * A delivery report whose `X-Request-Timestamp` is further than this from the server clock
     * (either way) is refused: a captured report cannot be replayed later.
     */
    val callbackMaxAge: Duration = Duration.ofMinutes(5)
) {
    init {
        require(callbackMaxAge.isPositive) { "app.auth.telegram-gateway.callback-max-age must be positive" }
        // The reissued code inherits the old one's expiry: with ttl equal to the lifetime, the
        // report arrives when the verification has already expired and nothing could be sent.
        require(!fallbackOnUndelivered || ttlSeconds < CODE_LIFETIME_SECONDS) {
            "app.auth.telegram-gateway.fallback-on-undelivered=true needs app.auth.telegram-gateway.ttl-seconds " +
                "below the phone code lifetime ($CODE_LIFETIME_SECONDS s), got $ttlSeconds: an undelivered code " +
                "is only reported after ttl, when the code it would replace has expired"
        }
    }

    companion object {
        const val CODE_LIFETIME_SECONDS = VerificationCodeService.PHONE_EXPIRY_MINUTES * 60
    }
}
