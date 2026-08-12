package kz.innlab.consumer

import kz.innlab.starter.authentication.controller.AppleAuthController
import kz.innlab.starter.authentication.controller.GoogleAuthController
import kz.innlab.starter.authentication.controller.LocalAuthController
import kz.innlab.starter.authentication.controller.PhoneAuthController
import kz.innlab.starter.authentication.controller.TelegramAuthController
import kz.innlab.starter.authentication.cookie.AuthCookieWriter
import kz.innlab.starter.authentication.service.AppleOAuth2Service
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
    fun `firebase push service follows the firebase flag`() {
        consumerContextRunner("app.firebase.enabled=true")
            .withStubFirebaseApp()
            .run { assertThat(it).hasSingleBean(FirebasePushService::class.java) }

        consumerContextRunner("app.firebase.enabled=false")
            .run { assertThat(it).doesNotHaveBean(FirebasePushService::class.java) }
    }
}
