package kz.innlab.starter.authentication.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.config.ActiveDirectoryProperties
import kz.innlab.starter.config.RateLimitProperties
import kz.innlab.starter.shared.ratelimit.RateLimitExceededException
import kz.innlab.starter.shared.ratelimit.RateLimiter
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service

/**
 * Active Directory login, by domain password or by Kerberos ticket. Both end in the same user,
 * linked by the account's directory id.
 *
 * Deliberately NOT @Transactional: every directory call is network I/O and must not hold a DB
 * connection. The DB work happens in the transactional [UserService.syncDirectoryUser].
 */
@Service
@ConditionalOnProperty(name = ["app.auth.active-directory.enabled"], havingValue = "true")
class ActiveDirectoryAuthService(
    private val directoryClient: DirectoryClient,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer,
    private val properties: ActiveDirectoryProperties,
    private val rateLimiter: RateLimiter,
    private val rateLimitProperties: RateLimitProperties
) : RefreshAccountCheck {

    companion object {
        private val logger = LoggerFactory.getLogger(ActiveDirectoryAuthService::class.java)
        private const val INVALID_CREDENTIALS = "Invalid username or password"
    }

    private val policy = DirectoryAccessPolicy(properties.access)

    fun login(login: String, password: String): AuthResponse {
        // Each wrong password also counts toward the domain's lockout policy. This limit has to trip
        // first, or anyone could lock a colleague out of Windows and 1C through this endpoint.
        val rule = rateLimitProperties.directoryLogin
        val key = "ad-login:${JndiDirectoryClient.normalizeLogin(login).lowercase()}"
        if (rateLimitProperties.enabled && !rateLimiter.tryAcquire(key, rule.maxAttempts, rule.windowSeconds)) {
            throw RateLimitExceededException("Too many login attempts. Try again later.", rule.windowSeconds)
        }

        // One message for every refusal: telling "no such account" from "wrong password" lists the domain's accounts.
        val account = directoryClient.authenticate(login, password)
            ?: throw BadCredentialsException(INVALID_CREDENTIALS)
        val response = issueFor(account) ?: throw BadCredentialsException(INVALID_CREDENTIALS)
        rateLimiter.reset(key)
        return response
    }

    /** [principal] is the Kerberos client principal (`ivanov@CORP.LOCAL`), already validated. */
    fun loginWithKerberos(principal: String): AuthResponse {
        val account = directoryClient.findByLogin(principal.substringBefore('@'))
        if (account == null) {
            logger.info("Kerberos principal {} has no directory account", principal)
            throw BadCredentialsException("Account is not available")
        }
        return issueFor(account) ?: throw BadCredentialsException("Account is not available")
    }

    private fun issueFor(account: DirectoryAccount): AuthResponse? {
        if (!account.enabled) {
            logger.info("Directory account {} is disabled or expired", account.dn)
            return null
        }
        if (!policy.isAllowed(account.groups)) {
            logger.info("Directory account {} is not in a required group", account.dn)
            return null
        }
        return authTokenIssuer.issue(sync(account))
    }

    private fun sync(account: DirectoryAccount): User = userService.syncDirectoryUser(
        provider = AuthProvider.ACTIVE_DIRECTORY,
        providerId = account.id,
        email = account.email ?: "",
        name = account.displayName,
        groups = policy.names(account.groups),
        admin = policy.isAdmin(account.groups),
        syncRoles = policy.syncRoles
    )

    /**
     * Ends the sessions of an account that was deleted, disabled, expired or removed from the
     * required groups since login, and syncs roles and groups for the rest.
     */
    override fun check(user: User) {
        if (!properties.refreshCheck.enabled) return
        val id = user.providerIds[AuthProvider.ACTIVE_DIRECTORY] ?: return
        val account = directoryClient.findById(id)
        if (account == null || !account.enabled || !policy.isAllowed(account.groups)) {
            logger.info("Directory account {} lost access; ending its sessions", id)
            throw BadCredentialsException("Account is no longer active")
        }
        sync(account)
    }
}
