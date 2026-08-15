package kz.innlab.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Per-channel waivers for the console-fallback checks in [ProductionSafetyConfig].
 *
 * The starter ships console-logging defaults for SMS, verification email and outgoing mail, and
 * the `prod` guard refuses to start while one of them is serving traffic. An application that uses
 * only some of those channels still has to answer for the others, and the single
 * `app.security.allow-console-fallbacks` switch could only waive all three at once — so an
 * application with no SMS provider had to disarm the mail checks as well to start at all.
 *
 * Waive one channel per property instead. Each waiver means "this application does not deliver
 * anything over that channel"; the remaining guards stay armed.
 */
@ConfigurationProperties(prefix = "app.security.console-fallbacks")
data class ConsoleFallbackProperties(
    /** No SMS is sent: phone OTP login and the `change-phone` endpoints are unused. */
    val allowSms: Boolean = false,
    /** No verification email is sent: registration verification, password reset and email change are unused. */
    val allowEmail: Boolean = false,
    /** No outgoing mail is sent: the `/api/v1/mail` endpoints are unused. */
    val allowMail: Boolean = false
)
