package kz.innlab.starter.authentication.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.service.ActiveDirectoryAuthService
import kz.innlab.starter.authentication.service.KerberosTicketValidator
import kz.innlab.starter.shared.error.ErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64

/**
 * Single sign-on for browsers on domain-joined machines. The page calls this endpoint with
 * `fetch(url, { method: 'POST', credentials: 'include' })`; the first answer is 401 with
 * `WWW-Authenticate: Negotiate`, the browser repeats the request with its Kerberos ticket, and the
 * second answer carries the starter's tokens. The browser only does this for sites in its intranet
 * zone (Edge/Chrome: `AuthServerAllowlist` policy; Firefox: `network.negotiate-auth.trusted-uris`).
 */
@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["app.auth.active-directory.enabled"], havingValue = "true")
@ConditionalOnProperty(name = ["app.auth.active-directory.kerberos.enabled"], havingValue = "true")
class KerberosAuthController(
    private val kerberosTicketValidator: KerberosTicketValidator,
    private val activeDirectoryAuthService: ActiveDirectoryAuthService
) {

    companion object {
        private const val NEGOTIATE = "Negotiate"
        /** Base64 of the NTLMSSP signature: the browser fell back to NTLM, which is not Kerberos. */
        private const val NTLM_PREFIX = "TlRMTVNT"
    }

    @Operation(summary = "Login with the browser's Kerberos ticket (SPNEGO)", security = [])
    @PostMapping("/active-directory/kerberos")
    fun kerberos(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?): ResponseEntity<*> {
        val token = authorization
            ?.takeIf { it.startsWith("$NEGOTIATE ", ignoreCase = true) }
            ?.substring(NEGOTIATE.length + 1)
            ?.trim()
        if (token.isNullOrEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, NEGOTIATE)
                .body(ErrorResponse(error = "Unauthorized", message = "Kerberos ticket required", status = 401))
        }
        if (token.startsWith(NTLM_PREFIX)) {
            // The client could not get a ticket for this host (no SPN, not in the domain, wrong URL).
            throw BadCredentialsException("NTLM is not supported; Kerberos ticket required")
        }
        val bytes = try {
            Base64.getDecoder().decode(token)
        } catch (_: IllegalArgumentException) {
            throw BadCredentialsException("Malformed Negotiate token")
        }

        val validation = kerberosTicketValidator.validate(bytes)
        val response: AuthResponse = activeDirectoryAuthService.loginWithKerberos(validation.principal)

        val builder = ResponseEntity.ok()
        validation.responseToken?.takeIf { it.isNotEmpty() }?.let {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "$NEGOTIATE ${Base64.getEncoder().encodeToString(it)}")
        }
        return builder.body(response)
    }
}
