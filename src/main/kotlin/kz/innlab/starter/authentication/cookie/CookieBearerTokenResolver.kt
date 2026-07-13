package kz.innlab.starter.authentication.cookie

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

/**
 * BearerTokenResolver that falls back to the access cookie when no Authorization header is present.
 *
 * The Authorization header ALWAYS wins (bearer / API-key clients are untouched) — delegation to
 * [DefaultBearerTokenResolver] preserves its malformed-header handling. Only when the delegate
 * finds no bearer token do we read the httpOnly access cookie.
 */
class CookieBearerTokenResolver(
    private val accessCookieName: String,
    private val delegate: BearerTokenResolver = DefaultBearerTokenResolver()
) : BearerTokenResolver {

    override fun resolve(request: HttpServletRequest): String? {
        val fromHeader = delegate.resolve(request)
        if (fromHeader != null) return fromHeader
        return request.cookies
            ?.firstOrNull { it.name == accessCookieName }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }
}
