package kz.innlab.starter.shared.security

import kz.innlab.starter.config.AuthSecurityProperties
import kz.innlab.starter.shared.error.ForbiddenOperationException
import kz.innlab.starter.shared.error.RecentLoginRequiredException
import kz.innlab.starter.shared.util.authTime
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Sudo mode for operations that hand over an account: they need a login younger than
 * `app.auth.security.admin-fresh-login-seconds`, judged by the token's `auth_time`.
 *
 * Enforced inside the services, not only in the starter's controllers: a consumer endpoint that
 * calls `AdminUserService` directly (a team-management screen, an invitation flow) used to skip
 * the check entirely. Call [requireFreshLogin] from your own endpoints that grant access in the
 * same way.
 *
 * Who the caller is comes from the security context:
 * - a JWT-authenticated user must have logged in recently;
 * - no authentication at all means code acting on its own (a scheduled job, a migration, a test
 *   calling the service) and is allowed;
 * - an anonymous or any other kind of authentication is refused: it cannot show a recent login.
 */
@Component
class FreshLoginGuard(private val authSecurityProperties: AuthSecurityProperties) {

    fun requireFreshLogin() {
        when (val authentication = SecurityContextHolder.getContext().authentication) {
            null -> return
            is JwtAuthenticationToken -> {
                val loggedInAt = authentication.token.authTime()
                val oldest = Instant.now().minusSeconds(authSecurityProperties.adminFreshLoginSeconds)
                if (loggedInAt == null || loggedInAt.isBefore(oldest)) {
                    throw RecentLoginRequiredException()
                }
            }
            is AnonymousAuthenticationToken ->
                throw ForbiddenOperationException("This operation needs an authenticated, recent login")
            else ->
                throw ForbiddenOperationException("This operation needs a recent login with a starter-issued token")
        }
    }
}
