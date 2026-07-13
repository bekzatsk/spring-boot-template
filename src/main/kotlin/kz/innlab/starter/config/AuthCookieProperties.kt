package kz.innlab.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for httpOnly cookie authentication mode.
 *
 * When [enabled] is false (default) the starter behaves exactly as before: access + refresh
 * tokens are returned only in the JSON body and no Set-Cookie headers are emitted. When enabled,
 * every AuthResponse-issuing endpoint additionally sets httpOnly cookies, and the access token is
 * read back from the access cookie for authentication (Authorization header always takes priority).
 */
@ConfigurationProperties(prefix = "app.auth.cookie")
data class AuthCookieProperties(
    /** Master switch. False = fully backward-compatible body-token behavior. */
    val enabled: Boolean = false,
    /** Secure attribute — cookie only sent over HTTPS. Keep true in production. */
    val secure: Boolean = true,
    /** SameSite policy: Strict | Lax | None. Strict is the primary CSRF defense for same-origin SPAs. */
    val sameSite: String = "Strict",
    /** Cookie domain. Empty = host-only cookie (recommended unless you need cross-subdomain). */
    val domain: String = "",
    /** Cookie path. */
    val path: String = "/",
    val accessCookieName: String = "access_token",
    val refreshCookieName: String = "refresh_token",
    /** Access cookie Max-Age in seconds. Negative = derive from access JWT TTL (app.auth.access-token.expiry-minutes). */
    val accessMaxAgeSeconds: Long = -1,
    /** Refresh cookie Max-Age in days. */
    val refreshMaxAgeDays: Long = 30,
    /** When true (and enabled), access/refresh tokens are omitted from the JSON body — only requiredActions remain. */
    val suppressBodyTokens: Boolean = false
)
