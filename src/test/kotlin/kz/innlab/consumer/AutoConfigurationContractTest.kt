package kz.innlab.consumer

import kz.innlab.starter.authentication.controller.AccountManagementController
import kz.innlab.starter.authentication.controller.AppleAuthController
import kz.innlab.starter.authentication.controller.AuthController
import kz.innlab.starter.authentication.controller.EmailOtpController
import kz.innlab.starter.authentication.controller.GoogleAuthController
import kz.innlab.starter.authentication.controller.LocalAuthController
import kz.innlab.starter.authentication.controller.PhoneAuthController
import kz.innlab.starter.authentication.controller.TelegramAuthController
import kz.innlab.starter.authentication.controller.TelegramWebhookController
import kz.innlab.starter.authentication.cookie.AuthResponseCookieAdvice
import kz.innlab.starter.authentication.exception.AuthExceptionHandler
import kz.innlab.starter.authentication.filter.ApiAccessDeniedHandler
import kz.innlab.starter.authentication.filter.ApiAuthenticationEntryPoint
import kz.innlab.starter.authentication.filter.RequiredActionFilter
import kz.innlab.starter.authentication.service.AccountManagementService
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.authentication.service.TelegramBotService
import kz.innlab.starter.authentication.service.AppleOAuth2Service
import kz.innlab.starter.authentication.service.AuthTokenIssuer
import kz.innlab.starter.authentication.service.EmailOtpService
import kz.innlab.starter.authentication.service.GoogleOAuth2Service
import kz.innlab.starter.authentication.service.LocalAuthService
import kz.innlab.starter.authentication.service.LocalUserDetailsService
import kz.innlab.starter.authentication.service.OtpDeliveryService
import kz.innlab.starter.authentication.service.PhoneOtpService
import kz.innlab.starter.authentication.service.RefreshTokenFamilyRevoker
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.TelegramAuthService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.authentication.service.VerificationAttemptRecorder
import kz.innlab.starter.authentication.service.VerificationCodeService
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.notification.controller.MailController
import kz.innlab.starter.notification.controller.NotificationController
import kz.innlab.starter.notification.controller.TopicAdminController
import kz.innlab.starter.notification.service.DeviceTokenService
import kz.innlab.starter.notification.service.ImapService
import kz.innlab.starter.notification.service.MailHistoryService
import kz.innlab.starter.notification.service.MailService
import kz.innlab.starter.notification.service.PushService
import kz.innlab.starter.notification.service.MailSendPolicy
import kz.innlab.starter.notification.service.NotificationDispatcher
import kz.innlab.starter.notification.service.NotificationPreferenceService
import kz.innlab.starter.notification.service.NotificationService
import kz.innlab.starter.notification.service.TopicService
import kz.innlab.starter.shared.ratelimit.RateLimiter
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.user.controller.AdminUserController
import kz.innlab.starter.user.controller.UserController
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.user.service.AdminUserService
import kz.innlab.starter.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.beans.Introspector
import kotlin.reflect.KClass

/**
 * Pins the starter's published bean contract: which beans a consumer application gets, and under
 * which names. Bean names matter beyond documentation — @Qualifier, @Async and
 * @ConditionalOnMissingBean(name = …) resolve by name, inside this starter and in consumer code.
 *
 * Built from [consumerContextRunner], not from `@SpringBootTest`: the earlier version of this test
 * booted `AuthStarterApplication`, whose package scan supplied any bean the auto-configuration
 * forgot, and so passed against an artifact whose cookie mode was dead.
 */
class AutoConfigurationContractTest {

    /** Registered regardless of which providers are switched on. */
    private val alwaysPresent: List<KClass<*>> = listOf(
        // authentication
        AccountManagementService::class,
        AuthTokenIssuer::class,
        OtpDeliveryService::class,
        RefreshTokenFamilyRevoker::class,
        RefreshTokenService::class,
        TokenService::class,
        VerificationAttemptRecorder::class,
        VerificationCodeService::class,
        AccountManagementController::class,
        AuthController::class,
        AuthResponseCookieAdvice::class,
        AuthExceptionHandler::class,
        ApiAccessDeniedHandler::class,
        ApiAuthenticationEntryPoint::class,
        RequiredActionFilter::class,
        // notification
        DeviceTokenService::class,
        ImapService::class,
        MailHistoryService::class,
        MailSendPolicy::class,
        NotificationDispatcher::class,
        NotificationPreferenceService::class,
        NotificationService::class,
        TopicService::class,
        MailController::class,
        NotificationController::class,
        TopicAdminController::class,
        // user
        AdminUserService::class,
        UserService::class,
        AdminUserController::class,
        UserController::class,
        // shared
        AfterCommitRunner::class,
        RateLimiter::class
    )

    /** Registered because the shared test configuration enables these providers. */
    private val enabledByTestConfig: List<KClass<*>> = listOf(
        GoogleOAuth2Service::class,
        GoogleAuthController::class,
        AppleOAuth2Service::class,
        AppleAuthController::class,
        TelegramAuthService::class,
        TelegramAuthController::class,
        TelegramWebhookController::class,
        LocalAuthService::class,
        LocalUserDetailsService::class,
        LocalAuthController::class,
        EmailOtpService::class,
        EmailOtpController::class,
        PhoneOtpService::class,
        PhoneAuthController::class
    )

    private val contractTypes = alwaysPresent + enabledByTestConfig

    private fun conventionalName(type: KClass<*>) = Introspector.decapitalize(type.java.simpleName)

    @Test
    fun `every starter bean is registered under its conventional name`() {
        consumerContextRunner().run { context ->
            val missing = contractTypes.filterNot { context.containsBean(conventionalName(it)) }
            assertThat(missing.map { it.simpleName })
                .describedAs("beans missing or renamed — consumer @Qualifier/@Async references break")
                .isEmpty()
        }
    }

    @Test
    fun `each conventional name resolves to the expected type`() {
        consumerContextRunner().run { context ->
            contractTypes.forEach { type ->
                assertThat(context.getBean(conventionalName(type)))
                    .describedAs("bean '%s'", conventionalName(type))
                    .isInstanceOf(type.java)
            }
        }
    }

    @Test
    fun `exactly one bean is published per starter type`() {
        consumerContextRunner().run { context ->
            val duplicated = contractTypes.filter {
                context.getBeanNamesForType(it.java).size != 1
            }
            assertThat(duplicated.map { it.simpleName }).isEmpty()
        }
    }

    /**
     * The ports a consumer plugs a real provider into. Each has a default implementation declared
     * with @ConditionalOnMissingBean, and each is consumed through ObjectProvider or Optional
     * somewhere in the starter — so a missing default is silent rather than fatal.
     */
    @Test
    fun `every replaceable service port resolves to exactly one implementation`() {
        val ports = listOf(
            SmsService::class,
            EmailService::class,
            MailService::class,
            PushService::class,
            RateLimiter::class,
            // telegram is enabled by the shared test configuration
            TelegramBotService::class
        )
        consumerContextRunner().run { context ->
            val unsatisfied = ports.filter { context.getBeanNamesForType(it.java).size != 1 }
            assertThat(unsatisfied.map { it.simpleName })
                .describedAs("no default implementation — injected optionally, so absence is silent")
                .isEmpty()
        }
    }

    @Test
    fun `spring data repositories are picked up from the starter package`() {
        consumerContextRunner().run { context ->
            assertThat(context.getBeanNamesForType(UserRepository::class.java)).hasSize(1)
        }
    }
}
