package kz.innlab.starter

import kz.innlab.starter.authentication.controller.AccountManagementController
import kz.innlab.starter.authentication.controller.AppleAuthController
import kz.innlab.starter.authentication.controller.AuthController
import kz.innlab.starter.authentication.controller.GoogleAuthController
import kz.innlab.starter.authentication.controller.LocalAuthController
import kz.innlab.starter.authentication.controller.PhoneAuthController
import kz.innlab.starter.authentication.controller.TelegramAuthController
import kz.innlab.starter.authentication.controller.TelegramWebhookController
import kz.innlab.starter.authentication.exception.AuthExceptionHandler
import kz.innlab.starter.authentication.filter.ApiAccessDeniedHandler
import kz.innlab.starter.authentication.filter.ApiAuthenticationEntryPoint
import kz.innlab.starter.authentication.filter.RequiredActionFilter
import kz.innlab.starter.authentication.service.AccountManagementService
import kz.innlab.starter.authentication.service.AppleOAuth2Service
import kz.innlab.starter.authentication.service.AuthTokenIssuer
import kz.innlab.starter.authentication.service.GoogleOAuth2Service
import kz.innlab.starter.authentication.service.LocalAuthService
import kz.innlab.starter.authentication.service.LocalUserDetailsService
import kz.innlab.starter.authentication.service.OtpDeliveryService
import kz.innlab.starter.authentication.service.PhoneOtpService
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.TelegramAuthService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.authentication.service.VerificationAttemptRecorder
import kz.innlab.starter.authentication.service.VerificationCodeService
import kz.innlab.starter.notification.controller.MailController
import kz.innlab.starter.notification.controller.NotificationController
import kz.innlab.starter.notification.controller.TopicAdminController
import kz.innlab.starter.notification.service.DeviceTokenService
import kz.innlab.starter.notification.service.ImapService
import kz.innlab.starter.notification.service.MailHistoryService
import kz.innlab.starter.notification.service.MailSendPolicy
import kz.innlab.starter.notification.service.NotificationDispatcher
import kz.innlab.starter.notification.service.NotificationPreferenceService
import kz.innlab.starter.notification.service.NotificationService
import kz.innlab.starter.notification.service.TopicService
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.user.controller.AdminUserController
import kz.innlab.starter.user.controller.UserController
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.user.service.AdminUserService
import kz.innlab.starter.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import java.beans.Introspector
import kotlin.reflect.KClass

/**
 * Pins the starter's published bean contract: which beans a consumer application gets, and
 * under which names. Bean names matter beyond documentation — @Qualifier, @Async and
 * @ConditionalOnMissingBean(name = …) resolve by name, inside this starter and in consumer code.
 *
 * Written before the auto-configuration was reworked, so it fails if that rework drops a bean
 * or renames one.
 */
@SpringBootTest
class AutoConfigurationContractTest {

    @Autowired
    private lateinit var context: ApplicationContext

    /** Registered regardless of which providers are switched on. */
    private val alwaysPresent: List<KClass<*>> = listOf(
        // authentication
        AccountManagementService::class,
        AuthTokenIssuer::class,
        OtpDeliveryService::class,
        RefreshTokenService::class,
        TokenService::class,
        VerificationAttemptRecorder::class,
        VerificationCodeService::class,
        AccountManagementController::class,
        AuthController::class,
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
        AfterCommitRunner::class
    )

    /** Registered because the test configuration enables these providers. */
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
        PhoneOtpService::class,
        PhoneAuthController::class
    )

    private fun conventionalName(type: KClass<*>) = Introspector.decapitalize(type.java.simpleName)

    @Test
    fun `every starter bean is registered under its conventional name`() {
        val missing = (alwaysPresent + enabledByTestConfig).filterNot { type ->
            context.containsBean(conventionalName(type))
        }
        assertThat(missing.map { it.simpleName })
            .describedAs("beans missing or renamed — consumer @Qualifier/@Async references break")
            .isEmpty()
    }

    @Test
    fun `each conventional name resolves to the expected type`() {
        (alwaysPresent + enabledByTestConfig).forEach { type ->
            val bean = context.getBean(conventionalName(type))
            assertThat(bean)
                .describedAs("bean '%s'", conventionalName(type))
                .isInstanceOf(type.java)
        }
    }

    @Test
    fun `exactly one bean is published per starter type`() {
        val duplicated = (alwaysPresent + enabledByTestConfig).filter { type ->
            context.getBeanNamesForType(type.java).size != 1
        }
        assertThat(duplicated.map { it.simpleName }).isEmpty()
    }

    @Test
    fun `spring data repositories are picked up from the starter package`() {
        assertThat(context.getBeanNamesForType(UserRepository::class.java)).hasSize(1)
    }
}
