package kz.innlab.starter.config

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Login against an on-premises Active Directory (or any LDAP directory with the attribute names
 * changed): domain username + password over LDAP, and optionally Kerberos (SPNEGO) for browsers on
 * domain-joined machines. Both identify the same directory account, so a user who signs in either
 * way is one starter user, linked by [Attributes.guid].
 *
 * The starter connects with [bindDn]/[bindPassword] (a read-only service account) to find users,
 * then binds as the user to check the password. A user is created on first login whether or not
 * `app.auth.registration.enabled` is on: the directory and [DirectoryAccess.requiredGroups] decide
 * who gets in.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.active-directory")
data class ActiveDirectoryProperties(
    val enabled: Boolean = false,
    /**
     * Domain controllers, tried in order: `ldaps://dc1.corp.local:636`. Use `ldaps://` — `ldap://`
     * sends user passwords in clear text, and a production profile refuses it unless
     * [allowPlaintext] is set.
     */
    @field:NotEmpty val urls: List<String> = listOf("ldaps://localhost:636"),
    val allowPlaintext: Boolean = false,
    /** Search root for users, e.g. `DC=corp,DC=local`. */
    val baseDn: String = "",
    /** Service account used to search the directory, e.g. `CN=svc-app,OU=Service,DC=corp,DC=local`. */
    val bindDn: String = "",
    val bindPassword: String = "",
    /**
     * `{0}` is the login with any `DOMAIN\` prefix removed; it is escaped before substitution.
     * The default accepts both `ivanov` (sAMAccountName) and `ivanov@corp.local` (UPN).
     */
    val userSearchFilter: String =
        "(&(objectCategory=person)(objectClass=user)(|(sAMAccountName={0})(userPrincipalName={0})))",
    /**
     * Expand nested group membership with AD's `LDAP_MATCHING_RULE_IN_CHAIN`. Off, only the
     * groups listed in the user's own `memberOf` count. Turn off for non-AD directories.
     */
    val nestedGroups: Boolean = true,
    /** Search root for the nested-group lookup; blank means [baseDn]. */
    val groupSearchBase: String = "",
    /** `follow` or `ignore`. AD answers searches from the domain root with referrals to other partitions. */
    val referral: String = "ignore",
    @field:Positive val connectTimeoutMillis: Long = 5_000,
    @field:Positive val readTimeoutMillis: Long = 10_000,
    val attributes: Attributes = Attributes(),
    val access: DirectoryAccess = DirectoryAccess(),
    val passwordLogin: PasswordLogin = PasswordLogin(),
    @field:Valid val kerberos: Kerberos = Kerberos(),
    val refreshCheck: RefreshCheck = RefreshCheck()
) {
    @get:AssertTrue(message = "base-dn, bind-dn and bind-password are required")
    val connectionConfigured: Boolean
        get() = !enabled || (baseDn.isNotBlank() && bindDn.isNotBlank() && bindPassword.isNotBlank())

    @get:AssertTrue(message = "referral must be 'follow' or 'ignore'")
    val referralSupported: Boolean
        get() = referral == "follow" || referral == "ignore"

    data class Attributes(
        /**
         * Stable account id, stored as the provider id. AD's `objectGUID` survives renames and
         * OU moves and is read as binary; any other attribute is read as a string.
         */
        val guid: String = "objectGUID",
        val email: String = "mail",
        val displayName: String = "displayName",
        val memberOf: String = "memberOf"
    )

    data class PasswordLogin(
        /** `POST /api/v1/auth/active-directory/login`. Turn off to allow Kerberos only. */
        val enabled: Boolean = true
    )

    data class Kerberos(
        /** `POST /api/v1/auth/active-directory/kerberos`. */
        val enabled: Boolean = false,
        /** The SPN registered for this application, e.g. `HTTP/app.corp.local@CORP.LOCAL`. */
        val servicePrincipal: String = "",
        /** Path to the keytab exported for [servicePrincipal]. */
        val keytab: String = "",
        /** Optional path to `krb5.conf`; blank lets the JDK find the realm through DNS or its defaults. */
        val krb5Conf: String = "",
        val debug: Boolean = false
    ) {
        @get:AssertTrue(message = "service-principal and keytab are required when Kerberos is enabled")
        val configured: Boolean
            get() = !enabled || (servicePrincipal.isNotBlank() && keytab.isNotBlank())
    }

    data class RefreshCheck(
        /**
         * Before each refresh, look the account up in the directory again: a deleted, disabled or
         * expired account, or one removed from the required groups, loses every session. Roles,
         * groups and name are synced at the same time. Off, a fired employee keeps working until
         * the refresh token expires.
         */
        val enabled: Boolean = true
    )
}

/**
 * Who may sign in through a directory provider and which starter roles they get. Shared by Active
 * Directory and Microsoft Entra ID / ADFS.
 *
 * Group entries match a group's full DN (`CN=App Users,OU=Groups,DC=corp,DC=local`) when they
 * contain `=`, otherwise its name (`App Users`) — case-insensitive either way. For Entra ID a group
 * is what the token's group claims hold: object ids, or app role values.
 */
data class DirectoryAccess(
    /** Users outside every one of these groups cannot sign in. Empty lets every account in. */
    val requiredGroups: List<String> = emptyList(),
    /** Members of any of these groups get `ADMIN`. */
    val adminGroups: List<String> = emptyList(),
    /**
     * Set `ADMIN` from [adminGroups] on every login (and refresh, for Active Directory), removing
     * it from users who left the groups. Off, `ADMIN` is granted from the groups but never taken
     * away, so it can also be managed in the application.
     */
    val syncRoles: Boolean = true,
    /**
     * Groups stored on the user and published in the `groups` claim, matched like the lists above.
     * Empty publishes every group — with nested membership an AD user can be in hundreds, which
     * bloats every access token and does not fit a cookie (4 KB) in cookie mode.
     */
    val publishedGroups: List<String> = emptyList()
)
