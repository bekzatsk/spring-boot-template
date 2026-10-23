package kz.innlab.starter.authentication.service

import kz.innlab.starter.config.DirectoryAccess
import javax.naming.InvalidNameException
import javax.naming.ldap.LdapName

/**
 * Applies [DirectoryAccess] to the groups a directory reports for a user: may they sign in, are
 * they an admin, and which names go into the `groups` claim.
 *
 * A group is either a DN (`CN=App Users,OU=Groups,DC=corp,DC=local`) or a plain value (an Entra ID
 * object id or app role). A configured entry containing `=` is compared with the DN, any other
 * entry with the group's name — the CN of a DN, or the plain value itself.
 */
class DirectoryAccessPolicy(private val access: DirectoryAccess) {

    fun isAllowed(groups: Collection<String>): Boolean =
        access.requiredGroups.isEmpty() || access.requiredGroups.any { matchesAny(it, groups) }

    fun isAdmin(groups: Collection<String>): Boolean = access.adminGroups.any { matchesAny(it, groups) }

    val syncRoles: Boolean get() = access.syncRoles

    /**
     * Short names for storage and the `groups` claim — the CN of a DN, a plain value as is —
     * limited to [DirectoryAccess.publishedGroups] when that is set.
     */
    fun names(groups: Collection<String>): Set<String> = groups
        .filter { group -> access.publishedGroups.isEmpty() || access.publishedGroups.any { matches(it, group) } }
        .map { nameOf(it) }
        .toSortedSet()

    private fun matchesAny(entry: String, groups: Collection<String>): Boolean = groups.any { matches(entry, it) }

    private fun matches(entry: String, group: String): Boolean =
        if (entry.contains('=')) sameDn(entry, group) else entry.equals(nameOf(group), ignoreCase = true)

    private fun sameDn(a: String, b: String): Boolean = try {
        // LdapName equality ignores case and insignificant whitespace in each RDN.
        LdapName(a) == LdapName(b)
    } catch (_: InvalidNameException) {
        a.equals(b, ignoreCase = true)
    }

    companion object {
        /** `CN=App Users,OU=Groups,DC=corp,DC=local` → `App Users`; anything that is not a DN is returned as is. */
        fun nameOf(group: String): String {
            if (!group.contains('=')) return group
            return try {
                val name = LdapName(group)
                name.rdns.lastOrNull()?.value?.toString() ?: group
            } catch (_: InvalidNameException) {
                group
            }
        }
    }
}
