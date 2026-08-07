package kz.innlab.starter

import kz.innlab.starter.authentication.service.ConsoleSmsService
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.config.ProductionSafetyConfig
import kz.innlab.starter.notification.service.ConsoleEmailService
import kz.innlab.starter.notification.service.ConsoleMailService
import kz.innlab.starter.notification.service.EmailAttachment
import kz.innlab.starter.notification.service.MailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.UUID

/**
 * The guard is the last line of defence against a production deployment that silently
 * degrades: a fixed OTP override, an unauthenticated Telegram webhook, or console
 * fallbacks writing verification codes to the log instead of sending them.
 */
class ProductionSafetyConfigTest {

    /** Real provider stand-ins: not the Console* classes the guard rejects. */
    private class RealSmsService : SmsService {
        override fun sendCode(phone: String, code: String) = Unit
    }

    private class RealEmailService : EmailService {
        override fun sendCode(to: String, code: String, purpose: String) = Unit
    }

    private class RealMailService : MailService {
        override fun send(
            to: String,
            subject: String,
            textBody: String?,
            htmlBody: String?,
            attachments: List<EmailAttachment>
        ) = Unit

        override fun sendEmail(
            userId: UUID,
            to: String,
            subject: String,
            textBody: String?,
            htmlBody: String?,
            attachments: List<EmailAttachment>
        ): UUID = UUID.randomUUID()
    }

    private fun runner(vararg properties: String) = ApplicationContextRunner()
        .withInitializer { it.environment.setActiveProfiles("prod") }
        .withUserConfiguration(ProductionSafetyConfig::class.java)
        .withBean(SmsService::class.java, { RealSmsService() })
        .withBean(EmailService::class.java, { RealEmailService() })
        .withBean(MailService::class.java, { RealMailService() })
        .withPropertyValues(*properties)

    @Test
    fun `starts when real providers are configured and no dev override is set`() {
        runner().run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasBean("productionSafetyGuard")
        }
    }

    @Test
    fun `refuses to start when an SMS dev-code override is set`() {
        runner("app.auth.sms.dev-code=123456").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasStackTraceContaining("dev-code")
        }
    }

    @Test
    fun `refuses to start when a verification dev-code override is set`() {
        runner("app.auth.verification.dev-code=123456").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasStackTraceContaining("dev-code")
        }
    }

    @Test
    fun `refuses to start when Telegram is enabled without a webhook secret`() {
        runner("app.auth.telegram.enabled=true").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasStackTraceContaining("webhook-secret")
        }
    }

    @Test
    fun `starts when Telegram is enabled with a webhook secret`() {
        runner(
            "app.auth.telegram.enabled=true",
            "app.auth.telegram.webhook-secret=s3cret"
        ).run { context ->
            assertThat(context).hasNotFailed()
        }
    }

    @Test
    fun `refuses to start when the console SMS fallback is serving traffic`() {
        ApplicationContextRunner()
            .withInitializer { it.environment.setActiveProfiles("prod") }
            .withUserConfiguration(ProductionSafetyConfig::class.java)
            .withBean(SmsService::class.java, { ConsoleSmsService() })
            .withBean(EmailService::class.java, { RealEmailService() })
            .withBean(MailService::class.java, { RealMailService() })
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("console fallback")
            }
    }

    @Test
    fun `refuses to start when the console mail fallbacks are serving traffic`() {
        ApplicationContextRunner()
            .withInitializer { it.environment.setActiveProfiles("prod") }
            .withUserConfiguration(ProductionSafetyConfig::class.java)
            .withBean(SmsService::class.java, { RealSmsService() })
            .withBean(EmailService::class.java, { ConsoleEmailService() })
            .withBean(MailService::class.java, { ConsoleMailService() })
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("console fallback")
            }
    }

    @Test
    fun `console fallbacks can be waived explicitly`() {
        ApplicationContextRunner()
            .withInitializer { it.environment.setActiveProfiles("prod") }
            .withUserConfiguration(ProductionSafetyConfig::class.java)
            .withBean(SmsService::class.java, { ConsoleSmsService() })
            .withBean(EmailService::class.java, { ConsoleEmailService() })
            .withBean(MailService::class.java, { ConsoleMailService() })
            .withPropertyValues("app.security.allow-console-fallbacks=true")
            .run { context ->
                assertThat(context).hasNotFailed()
            }
    }

    @Test
    fun `guard is inactive outside the prod profile`() {
        ApplicationContextRunner()
            .withUserConfiguration(ProductionSafetyConfig::class.java)
            .withPropertyValues("app.auth.sms.dev-code=123456")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean("productionSafetyGuard")
            }
    }
}
