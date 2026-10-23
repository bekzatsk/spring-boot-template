package kz.innlab.starter.config

import jakarta.validation.constraints.AssertTrue
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Login with an OpenID Connect ID token from Microsoft Entra ID (Azure AD — the directory behind
 * Teams and Microsoft 365) or from an on-premises ADFS. The client obtains the token (MSAL, or
 * ADFS's OIDC flow) and posts it to `POST /api/v1/auth/microsoft`, exactly like Google and Apple.
 *
 * For Entra ID set [tenantId] and [clientId]; the issuer and key set follow from the tenant.
 * For ADFS set [issuerUri] (`https://adfs.corp.local/adfs`) and [jwkSetUri]
 * (`https://adfs.corp.local/adfs/discovery/keys`) instead.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.microsoft")
data class MicrosoftAuthProperties(
    val enabled: Boolean = false,
    /**
     * The directory (tenant) id — a GUID, because the token's issuer carries the GUID, not the
     * domain. `common`, `organizations` and `consumers` are refused: they would let any Microsoft
     * account in the world sign in.
     */
    val tenantId: String = "",
    /** Application (client) id of the app registration; the token's audience must match it. */
    val clientId: String = "",
    /** Overrides the issuer derived from [tenantId]. Required for ADFS. */
    val issuerUri: String = "",
    /** Overrides the key set derived from [tenantId]. Required for ADFS. */
    val jwkSetUri: String = "",
    /**
     * Claims read as group memberships for [access]. Entra ID puts group object ids in `groups`
     * (once the app registration emits group claims) and app roles in `roles`; ADFS uses `group`.
     */
    val groupClaims: List<String> = listOf("groups", "roles"),
    val access: DirectoryAccess = DirectoryAccess()
) {
    @get:AssertTrue(message = "tenant-id must be the tenant's directory id (a GUID), or set issuer-uri and jwk-set-uri")
    val singleTenant: Boolean
        get() = !enabled || (issuerUri.isNotBlank() && jwkSetUri.isNotBlank()) || TENANT_ID.matches(tenantId)

    @get:AssertTrue(message = "client-id is required")
    val clientIdSet: Boolean
        get() = !enabled || clientId.isNotBlank()

    /** Issuer the ID token must carry. */
    fun resolvedIssuer(): String = issuerUri.ifBlank { "https://login.microsoftonline.com/$tenantId/v2.0" }

    fun resolvedJwkSetUri(): String =
        jwkSetUri.ifBlank { "https://login.microsoftonline.com/$tenantId/discovery/v2.0/keys" }

    private companion object {
        val TENANT_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
