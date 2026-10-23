package kz.innlab.starter.authentication.service

/**
 * Port to the LDAP directory behind Active Directory login. The default is [JndiDirectoryClient];
 * declare a bean of this type to replace it (another LDAP library, a cache, a test double).
 *
 * Every method does network I/O: never call one inside a transaction.
 */
interface DirectoryClient {

    /**
     * Finds the account by login and checks its password by binding as it. Null when the account
     * does not exist or the password is wrong — the caller must not tell the two apart.
     * Throws [DirectoryUnavailableException] when the directory cannot be reached.
     */
    fun authenticate(login: String, password: String): DirectoryAccount?

    /** Finds an account by login (`ivanov`, `ivanov@corp.local`) without checking a password. */
    fun findByLogin(login: String): DirectoryAccount?

    /** Finds an account by the id stored as its provider id. */
    fun findById(id: String): DirectoryAccount?
}

/** What the starter needs to know about a directory account. */
data class DirectoryAccount(
    /** Stable id — the provider id of the linked user. */
    val id: String,
    val dn: String,
    val email: String?,
    val displayName: String?,
    /** Group DNs, nested membership included when the client expands it. */
    val groups: Set<String>,
    /** False for disabled, locked-by-expiry or otherwise unusable accounts. */
    val enabled: Boolean
)

/** The directory could not be reached or answered with an error. Answered with 503. */
class DirectoryUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
