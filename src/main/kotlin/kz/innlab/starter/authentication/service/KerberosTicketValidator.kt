package kz.innlab.starter.authentication.service

import kz.innlab.starter.config.ActiveDirectoryProperties
import org.ietf.jgss.GSSCredential
import org.ietf.jgss.GSSException
import org.ietf.jgss.GSSManager
import org.ietf.jgss.Oid
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CompletionException
import javax.security.auth.Subject
import javax.security.auth.kerberos.KerberosPrincipal
import javax.security.auth.login.AppConfigurationEntry
import javax.security.auth.login.Configuration
import javax.security.auth.login.LoginContext

/**
 * Validates the SPNEGO token a browser sends in `Authorization: Negotiate …`. Replace the bean to
 * plug in another Kerberos implementation.
 */
interface KerberosTicketValidator {

    /** Returns the client principal (`ivanov@CORP.LOCAL`); throws BadCredentialsException when the token is not valid. */
    fun validate(token: ByteArray): KerberosValidation
}

/** [responseToken] goes back in `WWW-Authenticate: Negotiate …` when the client asked for mutual authentication. */
class KerberosValidation(val principal: String, val responseToken: ByteArray?)

/**
 * [KerberosTicketValidator] on the JDK's GSS-API — no library needed. The service logs in once
 * from its keytab; the key is all an acceptor needs, so the login does not expire the way a
 * client's ticket-granting ticket does.
 */
class JgssKerberosTicketValidator(private val kerberos: ActiveDirectoryProperties.Kerberos) : KerberosTicketValidator {

    companion object {
        private val logger = LoggerFactory.getLogger(JgssKerberosTicketValidator::class.java)
        private val SPNEGO = Oid("1.3.6.1.5.5.2")
        private val KERBEROS_V5 = Oid("1.2.840.113554.1.2.2")
    }

    init {
        // A missing keytab is a deployment error: fail at startup, not at the first login.
        val keytab = File(kerberos.keytab)
        check(keytab.isFile && keytab.canRead()) {
            "app.auth.active-directory.kerberos.keytab is not a readable file: ${kerberos.keytab}"
        }
        if (kerberos.krb5Conf.isNotBlank()) {
            System.setProperty("java.security.krb5.conf", kerberos.krb5Conf)
        }
    }

    // Retried on the next request when it fails (the KDC may be down at startup).
    private val serviceSubject: Subject by lazy { login() }

    override fun validate(token: ByteArray): KerberosValidation {
        val subject = try {
            serviceSubject
        } catch (ex: Exception) {
            logger.error("Kerberos service login failed for {}: {}", kerberos.servicePrincipal, ex.toString())
            throw DirectoryUnavailableException("Kerberos is unavailable", ex)
        }
        return try {
            Subject.callAs(subject, Callable { accept(token) })
        } catch (ex: CompletionException) {
            val cause = ex.cause
            if (cause is GSSException) {
                logger.debug("Kerberos token rejected: {}", cause.toString())
                throw BadCredentialsException("Invalid Kerberos ticket")
            }
            throw cause ?: ex
        }
    }

    private fun accept(token: ByteArray): KerberosValidation {
        val manager = GSSManager.getInstance()
        val credential = manager.createCredential(
            null, GSSCredential.INDEFINITE_LIFETIME, arrayOf(SPNEGO, KERBEROS_V5), GSSCredential.ACCEPT_ONLY
        )
        val context = manager.createContext(credential)
        try {
            val response = context.acceptSecContext(token, 0, token.size)
            // Kerberos through SPNEGO completes in one round trip; anything else (NTLM) does not.
            if (!context.isEstablished) throw BadCredentialsException("Kerberos negotiation did not complete")
            return KerberosValidation(context.srcName.toString(), response)
        } finally {
            context.dispose()
            credential.dispose()
        }
    }

    private fun login(): Subject {
        val options = mapOf(
            "useKeyTab" to "true",
            "keyTab" to kerberos.keytab,
            "principal" to kerberos.servicePrincipal,
            "storeKey" to "true",
            "doNotPrompt" to "true",
            "isInitiator" to "false",
            "refreshKrb5Config" to "true",
            "debug" to kerberos.debug.toString()
        )
        val configuration = object : Configuration() {
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> = arrayOf(
                AppConfigurationEntry(
                    "com.sun.security.auth.module.Krb5LoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                    options
                )
            )
        }
        val subject = Subject(false, setOf(KerberosPrincipal(kerberos.servicePrincipal)), emptySet<Any>(), emptySet<Any>())
        LoginContext("", subject, null, configuration).login()
        logger.info("Kerberos service principal {} logged in from keytab", kerberos.servicePrincipal)
        return subject
    }
}
