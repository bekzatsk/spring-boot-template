package kz.innlab.starter.authentication.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.config.MicrosoftAuthProperties
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Service

/**
 * Login with a Microsoft Entra ID or ADFS ID token. The decoder pins the issuer to one tenant and
 * the audience to this application, so the email in the token is one the tenant's administrators
 * assigned — but it is still not used to take over an existing account (see
 * [UserService.syncDirectoryUser]).
 */
@Service
@ConditionalOnProperty(name = ["app.auth.microsoft.enabled"], havingValue = "true")
class MicrosoftOAuth2Service(
    @Qualifier("microsoftJwtDecoder")
    private val microsoftJwtDecoder: JwtDecoder,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer,
    private val properties: MicrosoftAuthProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MicrosoftOAuth2Service::class.java)
        private val EMAIL_CLAIMS = listOf("email", "preferred_username", "upn", "unique_name")
    }

    private val policy = DirectoryAccessPolicy(properties.access)

    // Deliberately NOT @Transactional: decoding fetches the tenant's key set over HTTP and must not
    // hold a DB connection. DB work happens in the transactional userService call.
    fun authenticate(idToken: String): AuthResponse {
        val jwt = try {
            microsoftJwtDecoder.decode(idToken)
        } catch (ex: Exception) {
            // Detail stays server-side: decoder messages can expose network and parser state.
            logger.warn("Microsoft ID token validation failed: {}", ex.message, ex)
            throw BadCredentialsException("Invalid Microsoft ID token", ex)
        }

        // `oid` is the user's id across every application in the tenant; `sub` differs per
        // application and is all ADFS has.
        val providerId = jwt.getClaimAsString("oid") ?: jwt.subject
            ?: throw BadCredentialsException("Microsoft ID token has no subject")

        val groups = properties.groupClaims.flatMap { claimValues(jwt, it) }.toSet()
        if (!policy.isAllowed(groups)) {
            logger.info("Microsoft account {} is not in a required group", providerId)
            throw BadCredentialsException("Account is not allowed to sign in")
        }

        val user = userService.syncDirectoryUser(
            provider = AuthProvider.MICROSOFT,
            providerId = providerId,
            email = emailOf(jwt) ?: "",
            name = jwt.getClaimAsString("name"),
            groups = policy.names(groups),
            admin = policy.isAdmin(groups),
            syncRoles = policy.syncRoles
        )
        return authTokenIssuer.issue(user)
    }

    /** `email` is optional in Entra ID; the sign-in name is usually the address. */
    private fun emailOf(jwt: Jwt): String? = EMAIL_CLAIMS
        .mapNotNull { jwt.getClaimAsString(it) }
        .firstOrNull { it.contains('@') }

    private fun claimValues(jwt: Jwt, claim: String): List<String> = when (val value = jwt.claims[claim]) {
        is Collection<*> -> value.filterNotNull().map { it.toString() }
        is String -> listOf(value)
        else -> emptyList()
    }
}
