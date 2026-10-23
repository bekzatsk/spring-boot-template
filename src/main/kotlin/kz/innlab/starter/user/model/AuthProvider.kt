package kz.innlab.starter.user.model

enum class AuthProvider {
    GOOGLE,
    APPLE,
    LOCAL,
    TELEGRAM,
    /** On-premises Active Directory, by password or Kerberos; the provider id is the account's objectGUID. */
    ACTIVE_DIRECTORY,
    /** Microsoft Entra ID or ADFS (OpenID Connect); the provider id is the `oid` claim, or `sub`. */
    MICROSOFT
}
