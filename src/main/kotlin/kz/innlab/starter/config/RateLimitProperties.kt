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
    @field:Valid val changePassword: Rule = Rule(maxAttempts = 5, windowSeconds = 300),
    /**
     * Code checks per identifier and purpose, across codes. The per-code limit alone resets with
     * every new code, which leaves three guesses a minute for as long as the attacker likes.
     * The window is long on purpose: anyone who knows the address can use up the budget, so
     * this trades a day of code login for the target against an open-ended guessing run.
     */
    @field:Valid val otpVerify: Rule = Rule(maxAttempts = 10, windowSeconds = 86_400),
    /**
     * Codes sent per client address, across purposes. The per-identifier cooldown alone lets one
     * client walk through phone numbers and run up the SMS bill (toll fraud). Behind a reverse
     * proxy set `server.forward-headers-strategy` so the client address is the real one, or every
     * request shares the proxy's address and this becomes a global limit.
     */
    @field:Valid val codeSendPerClient: Rule = Rule(maxAttempts = 20, windowSeconds = 3_600),
    /**
     * Codes sent per purpose across all clients: a circuit breaker that caps the cost of a
     * distributed attack. When it trips, every user waits — size it to your real traffic.
     */
    @field:Valid val codeSendPerPurpose: Rule = Rule(maxAttempts = 1_000, windowSeconds = 3_600)
) {
    data class Rule(
        @field:Positive val maxAttempts: Int = 10,
        @field:Positive val windowSeconds: Long = 300
    )
}
