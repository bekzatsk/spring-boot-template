package kz.innlab.starter.authentication.cookie

import jakarta.servlet.http.HttpServletRequest
import kz.innlab.starter.config.AuthCookieProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/**
 * Central builder/reader for auth cookies. Only registered when `app.auth.cookie.enabled=true`,
 * so downstream components inject it via ObjectProvider and treat absence as "cookie mode off".
 */
@Component
@ConditionalOnProperty(name = ["app.auth.cookie.enabled"], havingValue = "true")
class AuthCookieWriter(
    private val props: AuthCookieProperties,
    @Value("\${app.auth.access-token.expiry-minutes:15}")
    private val accessExpiryMinutes: Long
) {

    val accessCookieName: String get() = props.accessCookieName
    val refreshCookieName: String get() = props.refreshCookieName

    private fun accessMaxAgeSeconds(): Long =
        if (props.accessMaxAgeSeconds >= 0) props.accessMaxAgeSeconds else accessExpiryMinutes * 60

    private fun refreshMaxAgeSeconds(): Long = props.refreshMaxAgeDays * 86_400

    /** Set-Cookie for the access token. */
    fun accessCookie(token: String): ResponseCookie = build(props.accessCookieName, token, accessMaxAgeSeconds())

    /** Set-Cookie for the refresh token. */
    fun refreshCookie(token: String): ResponseCookie = build(props.refreshCookieName, token, refreshMaxAgeSeconds())

    /** Set-Cookie that expires the access cookie immediately (logout). */
    fun clearAccessCookie(): ResponseCookie = build(props.accessCookieName, "", 0)

    /** Set-Cookie that expires the refresh cookie immediately (logout). */
    fun clearRefreshCookie(): ResponseCookie = build(props.refreshCookieName, "", 0)

    /** Read the refresh token from its cookie, or null when absent/blank. */
    fun readRefreshToken(request: HttpServletRequest): String? = readCookie(request, props.refreshCookieName)

    /** Whether the JSON body should omit access/refresh tokens. */
    fun suppressBodyTokens(): Boolean = props.suppressBodyTokens

    private fun build(name: String, value: String, maxAgeSeconds: Long): ResponseCookie {
        val builder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(props.secure)
            .path(props.path)
            .sameSite(props.sameSite)
            .maxAge(maxAgeSeconds)
        if (props.domain.isNotBlank()) {
            builder.domain(props.domain)
        }
        return builder.build()
    }

    private fun readCookie(request: HttpServletRequest, name: String): String? =
        request.cookies
            ?.firstOrNull { it.name == name }
            ?.value
            ?.takeIf { it.isNotBlank() }
}
