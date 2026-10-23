package kz.innlab.starter.authentication.service

import kz.innlab.starter.config.DirectoryAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DirectoryMappingTest {

    @Test
    fun `objectGUID bytes convert to the GUID string AD tools show`() {
        // {3F2504E0-4F89-11D3-9A0C-0305E82C3301} as stored by AD: first three fields little-endian.
        val bytes = byteArrayOf(
            0xE0.toByte(), 0x04, 0x25, 0x3F, 0x89.toByte(), 0x4F, 0xD3.toByte(), 0x11,
            0x9A.toByte(), 0x0C, 0x03, 0x05, 0xE8.toByte(), 0x2C, 0x33, 0x01
        )
        assertThat(JndiDirectoryClient.guidToString(bytes)).isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301")
        assertThat(JndiDirectoryClient.guidToBytes("3f2504e0-4f89-11d3-9a0c-0305e82c3301")).isEqualTo(bytes)
        assertThat(JndiDirectoryClient.guidToBytes("not-a-guid")).isNull()
    }

    @Test
    fun `logins lose their domain prefix`() {
        assertThat(JndiDirectoryClient.normalizeLogin(" CORP\\ivanov ")).isEqualTo("ivanov")
        assertThat(JndiDirectoryClient.normalizeLogin("ivanov@corp.local")).isEqualTo("ivanov@corp.local")
    }

    private val groups = setOf(
        "CN=App Users,OU=Groups,DC=corp,DC=local",
        "CN=App Admins,OU=Groups,DC=corp,DC=local"
    )

    @Test
    fun `groups match by name or by DN, ignoring case`() {
        val byName = DirectoryAccessPolicy(DirectoryAccess(requiredGroups = listOf("app users")))
        val byDn = DirectoryAccessPolicy(DirectoryAccess(requiredGroups = listOf("cn=app users, ou=groups, dc=corp, dc=local")))
        val other = DirectoryAccessPolicy(DirectoryAccess(requiredGroups = listOf("CN=App Users,OU=Other,DC=corp,DC=local")))

        assertThat(byName.isAllowed(groups)).isTrue()
        assertThat(byDn.isAllowed(groups)).isTrue()
        assertThat(other.isAllowed(groups)).describedAs("same name, different OU").isFalse()
    }

    @Test
    fun `no required groups lets everyone in`() {
        assertThat(DirectoryAccessPolicy(DirectoryAccess()).isAllowed(emptySet())).isTrue()
    }

    @Test
    fun `admin groups and claim names`() {
        val policy = DirectoryAccessPolicy(DirectoryAccess(adminGroups = listOf("App Admins")))
        assertThat(policy.isAdmin(groups)).isTrue()
        assertThat(policy.isAdmin(setOf("CN=App Users,OU=Groups,DC=corp,DC=local"))).isFalse()
        assertThat(policy.names(groups + "plain-object-id")).containsExactly("App Admins", "App Users", "plain-object-id")
    }

    @Test
    fun `published groups limit what reaches the token`() {
        val policy = DirectoryAccessPolicy(DirectoryAccess(publishedGroups = listOf("App Users", "plain-object-id")))
        assertThat(policy.names(groups + "plain-object-id")).containsExactly("App Users", "plain-object-id")
    }
}
