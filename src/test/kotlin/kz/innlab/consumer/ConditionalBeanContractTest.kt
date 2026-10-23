package kz.innlab.consumer

import kz.innlab.starter.authentication.controller.ActiveDirectoryAuthController
import kz.innlab.starter.authentication.controller.AppleAuthController
import kz.innlab.starter.authentication.controller.KerberosAuthController
import kz.innlab.starter.authentication.controller.MicrosoftAuthController
import kz.innlab.starter.authentication.controller.EmailOtpController
import kz.innlab.starter.authentication.controller.GoogleAuthController
import kz.innlab.starter.authentication.controller.LocalAuthController
import kz.innlab.starter.authentication.controller.PhoneAuthController
import kz.innlab.starter.authentication.controller.TelegramAuthController
import kz.innlab.starter.authentication.cookie.AuthCookieWriter
import kz.innlab.starter.authentication.service.ActiveDirectoryAuthService
import kz.innlab.starter.authentication.service.AppleOAuth2Service
import kz.innlab.starter.authentication.service.DirectoryClient
import kz.innlab.starter.authentication.service.KerberosTicketValidator
import kz.innlab.starter.authentication.service.MicrosoftOAuth2Service
import kz.innlab.starter.authentication.service.RefreshAccountCheck
import kz.innlab.starter.authentication.service.EmailOtpService
import kz.innlab.starter.authentication.service.GoogleOAuth2Service
import kz.innlab.starter.authentication.service.LocalAuthService
import kz.innlab.starter.authentication.service.PhoneOtpService
import kz.innlab.starter.authentication.service.TelegramAuthService
import kz.innlab.starter.notification.service.FirebasePushService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers the beans that are conditional on a property.
 *
 * [AutoConfigurationContractTest] proves the context starts and holds the unconditional beans; it
 * cannot prove anything about these. A conditional bean that is never declared does not break
 * startup — `AuthCookieWriter` is injected as `ObjectProvider<AuthCookieWriter>` by
 * `AuthResponseCookieAdvice` and `AuthController`, and absence means "cookie mode off" — so the
 * context comes up green with the feature silently dead. That is exactly what shipped in 0.1.0.
 *
 * Each case is asserted in both directions: present when the flag is on, absent when it is off.
 */
class ConditionalBeanContractTest {

    @Test
    fun `cookie writer is present when cookie mode is enabled`() {
        consumerContextRunner("app.auth.cookie.enabled=true")
            .run { assertThat(it).hasSingleBean(AuthCookieWriter::class.java) }
    }

    @Test
    fun `cookie writer is absent when cookie mode is disabled`() {
        consumerContextRunner("app.auth.cookie.enabled=false")
            .run { assertThat(it).doesNotHaveBean(AuthCookieWriter::class.java) }
    }

    @Test
    fun `cookie writer is absent when cookie mode is not configured at all`() {
        consumerContextRunner().run { assertThat(it).doesNotHaveBean(AuthCookieWriter::class.java) }
    }

    @Test
    fun `google provider beans follow the google flag`() {
        consumerContextRunner("app.auth.google.enabled=true", "app.auth.google.client-id=test")
            .run {
                assertThat(it).hasSingleBean(GoogleOAuth2Service::class.java)
                assertThat(it).hasSingleBean(GoogleAuthController::class.java)
            }
        consumerContextRunner("app.auth.google.enabled=false").run {
            assertThat(it).doesNotHaveBean(GoogleOAuth2Service::class.java)
            assertThat(it).doesNotHaveBean(GoogleAuthController::class.java)
        }
    }

    @Test
    fun `apple provider beans follow the apple flag`() {
        consumerContextRunner("app.auth.apple.enabled=true", "app.auth.apple.bundle-id=test")
            .run {
                assertThat(it).hasSingleBean(AppleOAuth2Service::class.java)
                assertThat(it).hasSingleBean(AppleAuthController::class.java)
            }
        consumerContextRunner("app.auth.apple.enabled=false").run {
            assertThat(it).doesNotHaveBean(AppleOAuth2Service::class.java)
            assertThat(it).doesNotHaveBean(AppleAuthController::class.java)
        }
    }

    @Test
    fun `telegram provider beans follow the telegram flag`() {
        consumerContextRunner("app.auth.telegram.enabled=true")
            .run {
                assertThat(it).hasSingleBean(TelegramAuthService::class.java)
                assertThat(it).hasSingleBean(TelegramAuthController::class.java)
            }
        consumerContextRunner("app.auth.telegram.enabled=false").run {
            assertThat(it).doesNotHaveBean(TelegramAuthService::class.java)
            assertThat(it).doesNotHaveBean(TelegramAuthController::class.java)
        }
    }

    @Test
    fun `local provider beans are on by default and can be switched off`() {
        consumerContextRunner().run {
            assertThat(it).hasSingleBean(LocalAuthService::class.java)
            assertThat(it).hasSingleBean(LocalAuthController::class.java)
        }
        consumerContextRunner("app.auth.local.enabled=false").run {
            assertThat(it).doesNotHaveBean(LocalAuthService::class.java)
            assertThat(it).doesNotHaveBean(LocalAuthController::class.java)
        }
    }

    @Test
    fun `phone provider beans are on by default and can be switched off`() {
        consumerContextRunner().run {
            assertThat(it).hasSingleBean(PhoneOtpService::class.java)
            assertThat(it).hasSingleBean(PhoneAuthController::class.java)
        }
        consumerContextRunner("app.auth.phone.enabled=false").run {
            assertThat(it).doesNotHaveBean(PhoneOtpService::class.java)
            assertThat(it).doesNotHaveBean(PhoneAuthController::class.java)
        }
    }

    @Test
    fun `email OTP provider beans are on by default and can be switched off`() {
        consumerContextRunner().run {
            assertThat(it).hasSingleBean(EmailOtpService::class.java)
            assertThat(it).hasSingleBean(EmailOtpController::class.java)
        }
        consumerContextRunner("app.auth.email-otp.enabled=false").run {
            assertThat(it).doesNotHaveBean(EmailOtpService::class.java)
            assertThat(it).doesNotHaveBean(EmailOtpController::class.java)
        }
    }

    @Test
    fun `active directory beans follow the active directory flag`() {
        consumerContextRunner(*ACTIVE_DIRECTORY_PROPERTIES).run {
            assertThat(it).hasSingleBean(DirectoryClient::class.java)
            assertThat(it).hasSingleBean(ActiveDirectoryAuthService::class.java)
            assertThat(it).hasSingleBean(ActiveDirectoryAuthController::class.java)
            assertThat(it).doesNotHaveBean(KerberosAuthController::class.java)
            assertThat(it).doesNotHaveBean(KerberosTicketValidator::class.java)
            assertThat(it.containsBean("directoryClient")).isTrue()
            assertThat(it.containsBean("activeDirectoryAuthService")).isTrue()
            assertThat(it.containsBean("activeDirectoryAuthController")).isTrue()
        }
        consumerContextRunner().run {
            assertThat(it).doesNotHaveBean(DirectoryClient::class.java)
            assertThat(it).doesNotHaveBean(ActiveDirectoryAuthService::class.java)
            assertThat(it).doesNotHaveBean(ActiveDirectoryAuthController::class.java)
        }
    }

    /**
     * AuthController reads the refresh-time checks through ObjectProvider: without this bean a
     * disabled directory account would keep refreshing, with nothing failing.
     */
    @Test
    fun `active directory registers its refresh-time account check`() {
        consumerContextRunner(*ACTIVE_DIRECTORY_PROPERTIES).run {
            assertThat(it.getBeanProvider(RefreshAccountCheck::class.java).orderedStream().toList())
                .singleElement()
                .isInstanceOf(ActiveDirectoryAuthService::class.java)
        }
        consumerContextRunner().run {
            assertThat(it).doesNotHaveBean(RefreshAccountCheck::class.java)
        }
    }

    @Test
    fun `kerberos beans follow the kerberos flag, and password login can be switched off`() {
        consumerContextRunner(
            *ACTIVE_DIRECTORY_PROPERTIES,
            "app.auth.active-directory.password-login.enabled=false",
            "app.auth.active-directory.kerberos.enabled=true",
            "app.auth.active-directory.kerberos.service-principal=HTTP/app.test@TEST",
            "app.auth.active-directory.kerberos.keytab=${keytab()}"
        ).run {
            assertThat(it).hasSingleBean(KerberosTicketValidator::class.java)
            assertThat(it).hasSingleBean(KerberosAuthController::class.java)
            assertThat(it.containsBean("kerberosTicketValidator")).isTrue()
            assertThat(it.containsBean("kerberosAuthController")).isTrue()
            assertThat(it).doesNotHaveBean(ActiveDirectoryAuthController::class.java)
            assertThat(it).hasSingleBean(ActiveDirectoryAuthService::class.java)
        }
    }

    @Test
    fun `kerberos refuses to start without a readable keytab`() {
        consumerContextRunner(
            *ACTIVE_DIRECTORY_PROPERTIES,
            "app.auth.active-directory.kerberos.enabled=true",
            "app.auth.active-directory.kerberos.service-principal=HTTP/app.test@TEST",
            "app.auth.active-directory.kerberos.keytab=/nonexistent/app.keytab"
        ).run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure).hasStackTraceContaining("is not a readable file")
        }
    }

    @Test
    fun `active directory refuses to start without a service account`() {
        consumerContextRunner("app.auth.active-directory.enabled=true", "app.auth.active-directory.base-dn=dc=test").run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure).hasStackTraceContaining("base-dn, bind-dn and bind-password are required")
        }
    }

    @Test
    fun `microsoft provider beans follow the microsoft flag`() {
        consumerContextRunner(
            "app.auth.microsoft.enabled=true",
            "app.auth.microsoft.tenant-id=9188040d-6c67-4c5b-b112-36a304b66dad",
            "app.auth.microsoft.client-id=test"
        ).run {
            assertThat(it).hasSingleBean(MicrosoftOAuth2Service::class.java)
            assertThat(it).hasSingleBean(MicrosoftAuthController::class.java)
            assertThat(it.containsBean("microsoftJwtDecoder")).isTrue()
            assertThat(it.containsBean("microsoftOAuth2Service")).isTrue()
        }
        consumerContextRunner().run {
            assertThat(it).doesNotHaveBean(MicrosoftOAuth2Service::class.java)
            assertThat(it).doesNotHaveBean(MicrosoftAuthController::class.java)
        }
    }

    @Test
    fun `microsoft refuses a multi-tenant endpoint`() {
        listOf("common", "organizations", "contoso.com").forEach { tenant ->
            consumerContextRunner(
                "app.auth.microsoft.enabled=true",
                "app.auth.microsoft.tenant-id=$tenant",
                "app.auth.microsoft.client-id=test"
            ).run {
                assertThat(it).hasFailed()
                assertThat(it.startupFailure).hasStackTraceContaining("tenant-id must be the tenant's directory id")
            }
        }
    }

    @Test
    fun `microsoft accepts ADFS through an explicit issuer and key set`() {
        consumerContextRunner(
            "app.auth.microsoft.enabled=true",
            "app.auth.microsoft.issuer-uri=https://adfs.corp.local/adfs",
            "app.auth.microsoft.jwk-set-uri=https://adfs.corp.local/adfs/discovery/keys",
            "app.auth.microsoft.client-id=test"
        ).run { assertThat(it).hasSingleBean(MicrosoftOAuth2Service::class.java) }
    }

    @Test
    fun `OTP code lengths reject unsupported values`() {
        consumerContextRunner("app.auth.phone.code-length=5").run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure).hasStackTraceContaining("code-length must be 4 or 6")
        }
        consumerContextRunner("app.auth.email-otp.code-length=8").run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure).hasStackTraceContaining("code-length must be 4 or 6")
        }
    }

    @Test
    fun `firebase push service follows the firebase flag`() {
        consumerContextRunner("app.firebase.enabled=true")
            .withStubFirebaseApp()
            .run { assertThat(it).hasSingleBean(FirebasePushService::class.java) }

        consumerContextRunner("app.firebase.enabled=false")
            .run { assertThat(it).doesNotHaveBean(FirebasePushService::class.java) }
    }
}
