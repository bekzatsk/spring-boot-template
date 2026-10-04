package kz.innlab.starter.authentication.cookie

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Collections
import java.util.Enumeration

/**
 * Cookie mode: presents the access cookie to the resource server as an `Authorization: Bearer`
 * header, when the request carries no Authorization header of its own (the header always wins).
 *
 * It runs **after** `CsrfFilter`, and that ordering is the point. The resource server tells CSRF
 * to skip any request its token resolver finds a bearer token in, on the grounds that a browser
 * never attaches one by itself. Reading the cookie inside the resolver (the former
 * `CookieBearerTokenResolver`) therefore switched CSRF off for every cookie-authenticated request —
 * exactly the requests CSRF exists for. Here CSRF judges the request as the browser sent it, and
 * only afterwards does the cookie become a bearer token.
 *
 * Not a bean: a Filter bean would also be registered as a servlet filter for every request.
 */
class AccessTokenCookieFilter(private val accessCookieName: String) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            chain.doFilter(request, response)
            return
        }
        val token = request.cookies
            ?.firstOrNull { it.name == accessCookieName }
            ?.value
            ?.takeIf { it.isNotBlank() }
        if (token == null) {
            chain.doFilter(request, response)
            return
        }
        chain.doFilter(BearerHeaderRequest(request, "Bearer $token"), response)
    }

    private class BearerHeaderRequest(
        request: HttpServletRequest,
        private val authorization: String
    ) : HttpServletRequestWrapper(request) {

        override fun getHeader(name: String): String? =
            if (name.equals(HttpHeaders.AUTHORIZATION, ignoreCase = true)) authorization else super.getHeader(name)

        override fun getHeaders(name: String): Enumeration<String> =
            if (name.equals(HttpHeaders.AUTHORIZATION, ignoreCase = true)) {
                Collections.enumeration(listOf(authorization))
            } else {
                super.getHeaders(name)
            }

        override fun getHeaderNames(): Enumeration<String> =
            Collections.enumeration(Collections.list(super.getHeaderNames()) + HttpHeaders.AUTHORIZATION)
    }
}
