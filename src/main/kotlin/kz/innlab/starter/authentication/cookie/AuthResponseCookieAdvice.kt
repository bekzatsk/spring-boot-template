package kz.innlab.starter.authentication.cookie

import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.TelegramVerifyResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * Centralized cookie issuance: any controller (starter or consumer) returning an [AuthResponse]
 * or a verified [TelegramVerifyResponse] gets access + refresh Set-Cookie headers automatically,
 * with no per-controller duplication.
 *
 * Inactive unless `app.auth.cookie.enabled=true` (AuthCookieWriter bean absent otherwise), so the
 * default body-token contract is untouched. When suppressBodyTokens is on, tokens are stripped
 * from the JSON body after being written to cookies.
 */
@RestControllerAdvice
class AuthResponseCookieAdvice(
    private val cookieWriter: ObjectProvider<AuthCookieWriter>
) : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        val writer = cookieWriter.ifAvailable ?: return body

        return when (body) {
            is AuthResponse -> {
                if (body.accessToken.isNotBlank()) addCookie(response, writer.accessCookie(body.accessToken))
                if (body.refreshToken.isNotBlank()) addCookie(response, writer.refreshCookie(body.refreshToken))
                if (writer.suppressBodyTokens()) mapOf("requiredActions" to body.requiredActions) else body
            }

            is TelegramVerifyResponse -> {
                if (body.verified) {
                    body.accessToken?.takeIf { it.isNotBlank() }?.let { addCookie(response, writer.accessCookie(it)) }
                    body.refreshToken?.takeIf { it.isNotBlank() }?.let { addCookie(response, writer.refreshCookie(it)) }
                    if (writer.suppressBodyTokens()) body.copy(accessToken = null, refreshToken = null) else body
                } else {
                    body
                }
            }

            else -> body
        }
    }

    private fun addCookie(response: ServerHttpResponse, cookie: ResponseCookie) {
        response.headers.add(HttpHeaders.SET_COOKIE, cookie.toString())
    }
}
