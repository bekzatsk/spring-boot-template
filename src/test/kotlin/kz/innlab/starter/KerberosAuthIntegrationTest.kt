package kz.innlab.starter

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.service.DirectoryAccount
import kz.innlab.starter.authentication.service.DirectoryClient
import kz.innlab.starter.authentication.service.KerberosTicketValidator
import kz.innlab.starter.authentication.service.KerberosValidation
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

/**
 * The SPNEGO exchange around the ticket check. The check itself needs a KDC and is the JDK's;
 * here the validator and the directory are stand-ins.
 */
@SpringBootTest(
    properties = [
        "app.auth.active-directory.enabled=true",
        "app.auth.active-directory.base-dn=dc=corp,dc=local",
        "app.auth.active-directory.bind-dn=cn=svc,dc=corp,dc=local",
        "app.auth.active-directory.bind-password=secret",
        "app.auth.active-directory.password-login.enabled=false",
        "app.auth.active-directory.kerberos.enabled=true",
        "app.auth.active-directory.kerberos.service-principal=HTTP/app.corp.local@CORP.LOCAL",
        "app.auth.active-directory.kerberos.keytab=/etc/app.keytab"
    ]
)
@AutoConfigureMockMvc
class KerberosAuthIntegrationTest {

    @MockitoBean private lateinit var kerberosTicketValidator: KerberosTicketValidator
    @MockitoBean private lateinit var directoryClient: DirectoryClient

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    private val ticket = Base64.getEncoder().encodeToString("kerberos-ticket".toByteArray())

    @BeforeEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    /** Mockito's any() returns null, which a Kotlin ByteArray parameter refuses. */
    private fun anyTicket(): ByteArray = any(ByteArray::class.java) ?: ByteArray(0)

    private fun kerberos(authorization: String? = null) = mockMvc.perform(
        post("/api/v1/auth/active-directory/kerberos").also { request ->
            authorization?.let { request.header(HttpHeaders.AUTHORIZATION, it) }
        }
    )

    @Test
    fun `without a ticket the browser is asked to negotiate`() {
        kerberos()
            .andExpect(status().isUnauthorized)
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Negotiate"))
    }

    @Test
    fun `a valid ticket signs the directory account in`() {
        `when`(kerberosTicketValidator.validate(anyTicket())).thenReturn(
            KerberosValidation("ivanov@CORP.LOCAL", "mutual".toByteArray())
        )
        `when`(directoryClient.findByLogin("ivanov")).thenReturn(
            DirectoryAccount(
                id = "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
                dn = "CN=Ivan Ivanov,OU=Users,DC=corp,DC=local",
                email = "ivanov@corp.local",
                displayName = "Ivan Ivanov",
                groups = emptySet(),
                enabled = true
            )
        )

        kerberos("Negotiate $ticket")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(header().string(
                HttpHeaders.WWW_AUTHENTICATE,
                "Negotiate ${Base64.getEncoder().encodeToString("mutual".toByteArray())}"
            ))

        val user = userRepository.findAll().single()
        assertThat(user.providerIds[AuthProvider.ACTIVE_DIRECTORY]).isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301")
    }

    @Test
    fun `a rejected ticket is 401`() {
        `when`(kerberosTicketValidator.validate(anyTicket())).thenThrow(BadCredentialsException("Invalid Kerberos ticket"))
        kerberos("Negotiate $ticket").andExpect(status().isUnauthorized)
    }

    @Test
    fun `an NTLM fallback is refused without reaching the validator`() {
        kerberos("Negotiate TlRMTVNTUAABAAAAB4IIog==").andExpect(status().isUnauthorized)
        verify(kerberosTicketValidator, never()).validate(anyTicket())
    }

    @Test
    fun `a principal without a directory account is refused`() {
        `when`(kerberosTicketValidator.validate(anyTicket())).thenReturn(KerberosValidation("ghost@CORP.LOCAL", null))
        kerberos("Negotiate $ticket").andExpect(status().isUnauthorized)
        assertThat(userRepository.findAll()).isEmpty()
    }

    @Test
    fun `password login is off when only Kerberos is enabled`() {
        mockMvc.perform(post("/api/v1/auth/active-directory/login"))
            .andExpect(status().isNotFound)
    }
}
