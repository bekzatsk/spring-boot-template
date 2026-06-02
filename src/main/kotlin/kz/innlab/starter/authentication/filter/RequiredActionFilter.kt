package kz.innlab.starter.authentication.filter

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kz.innlab.starter.config.AuthSecurityProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Enforces JWT `required_actions` claim. If the user's token carries pending actions
 * (e.g. UPDATE_PASSWORD), all endpoints except those in the allowed-paths whitelist
 * return 403 with `{"requiredActions": [...]}`.
 *
 * Runs after Spring's JWT auth filter — uses the resolved JwtAuthenticationToken.
 */
@Component
class RequiredActionFilter(
    private val properties: AuthSecurityProperties,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val pathMatcher = AntPathMatcher()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !properties.requiredAction.enabled

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth !is JwtAuthenticationToken) {
            filterChain.doFilter(request, response)
            return
        }

        val actions = extractActions(auth.token)
        if (actions.isEmpty()) {
            filterChain.doFilter(request, response)
            return
        }

        val path = request.requestURI
        if (isAllowed(path)) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                mapOf(
                    "error" to "Forbidden",
                    "message" to "Required actions must be completed",
                    "status" to 403,
                    "requiredActions" to actions
                )
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractActions(jwt: Jwt): List<String> {
        val raw = jwt.claims["required_actions"] ?: return emptyList()
        return (raw as? List<String>) ?: emptyList()
    }

    private fun isAllowed(path: String): Boolean =
        properties.requiredAction.allowedPaths.any { pattern ->
            pathMatcher.match(pattern, path)
        }
}
