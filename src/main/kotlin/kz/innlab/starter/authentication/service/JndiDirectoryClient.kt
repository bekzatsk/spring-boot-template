package kz.innlab.starter.authentication.service

import kz.innlab.starter.config.ActiveDirectoryProperties
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Hashtable
import java.util.UUID
import javax.naming.AuthenticationException
import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.NamingException
import javax.naming.PartialResultException
import javax.naming.SizeLimitExceededException
import javax.naming.directory.Attributes
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

/**
 * [DirectoryClient] over the JDK's own LDAP provider (JNDI), so the starter pulls in no LDAP
 * library — and none of Spring Boot's LDAP auto-configuration, whose health indicator would report
 * DOWN in every consumer application without a directory.
 *
 * A connection is opened per operation: logins are rare next to API calls, and a pooled
 * connection to a domain controller that restarted fails the next login instead.
 */
class JndiDirectoryClient(private val properties: ActiveDirectoryProperties) : DirectoryClient {

    companion object {
        private val logger = LoggerFactory.getLogger(JndiDirectoryClient::class.java)
        private const val OBJECT_GUID = "objectGUID"
        private const val USER_ACCOUNT_CONTROL = "userAccountControl"
        private const val ACCOUNT_EXPIRES = "accountExpires"
        private const val ACCOUNTDISABLE = 0x2
        /** AD's "never expires" values of accountExpires. */
        private val NEVER_EXPIRES = setOf(0L, Long.MAX_VALUE)
        /** Seconds between 1601-01-01 (Windows FILETIME epoch) and 1970-01-01. */
        private const val FILETIME_EPOCH_OFFSET_SECONDS = 11_644_473_600L
        /** AD's LDAP_MATCHING_RULE_IN_CHAIN: transitive group membership in one search. */
        private const val IN_CHAIN = "1.2.840.113556.1.4.1941"

        /** AD stores objectGUID with its first three fields little-endian. */
        fun guidToString(bytes: ByteArray): String {
            require(bytes.size == 16) { "objectGUID must be 16 bytes" }
            val buffer = ByteBuffer.wrap(bytes)
            val data1 = buffer.order(ByteOrder.LITTLE_ENDIAN).getInt(0).toLong() and 0xFFFFFFFFL
            val data2 = buffer.order(ByteOrder.LITTLE_ENDIAN).getShort(4).toLong() and 0xFFFFL
            val data3 = buffer.order(ByteOrder.LITTLE_ENDIAN).getShort(6).toLong() and 0xFFFFL
            val data4 = buffer.order(ByteOrder.BIG_ENDIAN).getLong(8)
            return UUID((data1 shl 32) or (data2 shl 16) or data3, data4).toString()
        }

        fun guidToBytes(guid: String): ByteArray? {
            val uuid = try { UUID.fromString(guid) } catch (_: IllegalArgumentException) { return null }
            val msb = uuid.mostSignificantBits
            return ByteBuffer.allocate(16)
                .order(ByteOrder.LITTLE_ENDIAN).putInt(0, (msb ushr 32).toInt())
                .putShort(4, (msb ushr 16).toShort())
                .putShort(6, msb.toShort())
                .order(ByteOrder.BIG_ENDIAN).putLong(8, uuid.leastSignificantBits)
                .array()
        }

        /** `CORP\ivanov` → `ivanov`; UPNs and plain names are kept. */
        fun normalizeLogin(login: String): String = login.trim().substringAfterLast('\\')
    }

    private val attributes = properties.attributes
    private val binaryGuid = attributes.guid.equals(OBJECT_GUID, ignoreCase = true)

    override fun authenticate(login: String, password: String): DirectoryAccount? {
        // An empty password turns an LDAP simple bind into an "unauthenticated" bind, which most
        // servers answer with success — every account would open with a blank password.
        if (password.isEmpty()) return null
        val account = findByLogin(login) ?: return null
        return if (bindSucceeds(account.dn, password)) account else null
    }

    override fun findByLogin(login: String): DirectoryAccount? {
        val normalized = normalizeLogin(login)
        if (normalized.isEmpty()) return null
        return withServiceContext { searchOne(it, properties.userSearchFilter, arrayOf(normalized)) }
    }

    override fun findById(id: String): DirectoryAccount? {
        val value: Any = if (binaryGuid) guidToBytes(id) ?: return null else id
        // JNDI escapes filter arguments; a byte[] argument becomes \xx octets.
        return withServiceContext { searchOne(it, "(${attributes.guid}={0})", arrayOf(value)) }
    }

    private fun searchOne(context: DirContext, filter: String, args: Array<Any>): DirectoryAccount? {
        val controls = SearchControls().apply {
            searchScope = SearchControls.SUBTREE_SCOPE
            // Two is enough to notice an ambiguous filter.
            countLimit = 2
            returningAttributes = arrayOf(
                attributes.guid, attributes.email, attributes.displayName, attributes.memberOf,
                USER_ACCOUNT_CONTROL, ACCOUNT_EXPIRES
            )
        }
        val results = collect(context.search(properties.baseDn, filter, args, controls))
        if (results.size > 1) {
            logger.warn("Directory search matched more than one account; refusing to pick one")
            return null
        }
        val result = results.firstOrNull() ?: return null
        return toAccount(context, result)
    }

    private fun toAccount(context: DirContext, result: SearchResult): DirectoryAccount? {
        val attrs = result.attributes
        val id = readId(attrs) ?: run {
            logger.warn("Directory account has no {} attribute; it cannot be linked", attributes.guid)
            return null
        }
        val dn = result.nameInNamespace
        val groups = values(attrs, attributes.memberOf).toMutableSet()
        if (properties.nestedGroups) groups += nestedGroups(context, dn)
        return DirectoryAccount(
            id = id,
            dn = dn,
            email = single(attrs, attributes.email)?.takeIf { it.isNotBlank() },
            displayName = single(attrs, attributes.displayName)?.takeIf { it.isNotBlank() },
            groups = groups,
            enabled = isEnabled(attrs)
        )
    }

    private fun nestedGroups(context: DirContext, userDn: String): Set<String> {
        val controls = SearchControls().apply {
            searchScope = SearchControls.SUBTREE_SCOPE
            returningAttributes = emptyArray()
        }
        val base = properties.groupSearchBase.ifBlank { properties.baseDn }
        return collect(context.search(base, "(member:$IN_CHAIN:={0})", arrayOf<Any>(userDn), controls))
            .map { it.nameInNamespace }
            .toSet()
    }

    private fun readId(attrs: Attributes): String? {
        val value = attrs.get(attributes.guid)?.get() ?: return null
        return when (value) {
            is ByteArray -> if (binaryGuid) guidToString(value) else String(value, Charsets.UTF_8)
            else -> value.toString()
        }
    }

    private fun isEnabled(attrs: Attributes): Boolean {
        val control = single(attrs, USER_ACCOUNT_CONTROL)?.toIntOrNull()
        if (control != null && control and ACCOUNTDISABLE != 0) return false
        val expires = single(attrs, ACCOUNT_EXPIRES)?.toLongOrNull()
        if (expires != null && expires !in NEVER_EXPIRES) {
            // FILETIME: 100-nanosecond intervals since 1601-01-01.
            val expiresAt = Instant.ofEpochSecond(
                expires / 10_000_000 - FILETIME_EPOCH_OFFSET_SECONDS,
                (expires % 10_000_000) * 100
            )
            if (expiresAt.isBefore(Instant.now())) return false
        }
        return true
    }

    private fun single(attrs: Attributes, name: String): String? = attrs.get(name)?.get()?.toString()

    private fun values(attrs: Attributes, name: String): List<String> {
        val attribute = attrs.get(name) ?: return emptyList()
        return (0 until attribute.size()).map { attribute.get(it).toString() }
    }

    /**
     * AD answers a search from the domain root with continuation references to other partitions;
     * with `referral: ignore` JNDI reports them as PartialResultException after the real results.
     */
    private fun collect(results: NamingEnumeration<SearchResult>): List<SearchResult> {
        val collected = mutableListOf<SearchResult>()
        try {
            while (results.hasMore()) collected += results.next()
        } catch (_: PartialResultException) {
            // References only; everything this server holds has been returned.
        } catch (_: SizeLimitExceededException) {
            // Raised past the count limit, after the results within it were delivered.
        } finally {
            results.close()
        }
        return collected
    }

    private fun bindSucceeds(dn: String, password: String): Boolean {
        val context = try {
            InitialDirContext(environment(dn, password))
        } catch (_: AuthenticationException) {
            // Wrong password, but also locked, disabled or expired: AD reports all of them here.
            return false
        } catch (ex: NamingException) {
            throw DirectoryUnavailableException("Directory is unavailable", ex)
        }
        context.close()
        return true
    }

    private fun <T> withServiceContext(block: (DirContext) -> T): T {
        val context = try {
            InitialDirContext(environment(properties.bindDn, properties.bindPassword))
        } catch (ex: AuthenticationException) {
            logger.error("Directory rejected the service account {}", properties.bindDn)
            throw DirectoryUnavailableException("Directory is unavailable", ex)
        } catch (ex: NamingException) {
            logger.warn("Directory is unreachable: {}", ex.toString())
            throw DirectoryUnavailableException("Directory is unavailable", ex)
        }
        try {
            return block(context)
        } catch (ex: NamingException) {
            logger.warn("Directory search failed: {}", ex.toString())
            throw DirectoryUnavailableException("Directory is unavailable", ex)
        } finally {
            context.close()
        }
    }

    private fun environment(principal: String, credentials: String): Hashtable<String, Any> =
        Hashtable<String, Any>().apply {
            put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
            // Several URLs separated by spaces are tried in order.
            put(Context.PROVIDER_URL, properties.urls.joinToString(" "))
            put(Context.SECURITY_AUTHENTICATION, "simple")
            put(Context.SECURITY_PRINCIPAL, principal)
            put(Context.SECURITY_CREDENTIALS, credentials)
            put(Context.REFERRAL, properties.referral)
            put("com.sun.jndi.ldap.connect.timeout", properties.connectTimeoutMillis.toString())
            put("com.sun.jndi.ldap.read.timeout", properties.readTimeoutMillis.toString())
            if (binaryGuid) put("java.naming.ldap.attributes.binary", attributes.guid)
        }
}
