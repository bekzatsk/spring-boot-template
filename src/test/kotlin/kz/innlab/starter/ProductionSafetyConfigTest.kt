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
import java.io.PrintWriter
import java.io.StringWriter
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

    /** Console fallback on the named channels, real providers everywhere else. */
    private fun runnerWithConsole(vararg channels: Channel) = ApplicationContextRunner()
        .withInitializer { it.environment.setActiveProfiles("prod") }
        .withUserConfiguration(ProductionSafetyConfig::class.java)
        .withBean(
            SmsService::class.java,
            { if (Channel.SMS in channels) ConsoleSmsService() else RealSmsService() }
        )
        .withBean(
            EmailService::class.java,
            { if (Channel.EMAIL in channels) ConsoleEmailService() else RealEmailService() }
        )
        .withBean(
            MailService::class.java,
            { if (Channel.MAIL in channels) ConsoleMailService() else RealMailService() }
        )

    private enum class Channel { SMS, EMAIL, MAIL }

    /**
     * The guard's messages sit on a nested IllegalStateException, so an assertion on the top-level
     * failure's own message would pass no matter what the guard reported.
     */
    private fun stackTraceOf(failure: Throwable?): String =
        StringWriter().also { checkNotNull(failure).printStackTrace(PrintWriter(it)) }.toString()

    @Test
    fun `refuses to start when the console SMS fallback is serving traffic`() {
        runnerWithConsole(Channel.SMS).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasStackTraceContaining("SmsService is the console fallback")
        }
    }

    @Test
    fun `refuses to start when the console mail fallbacks are serving traffic`() {
        runnerWithConsole(Channel.EMAIL, Channel.MAIL).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasStackTraceContaining("EmailService is the console fallback")
            assertThat(context.startupFailure).hasStackTraceContaining("MailService is the console fallback")
        }
    }

    @Test
    fun `every console fallback can be waived at once with the legacy switch`() {
        runnerWithConsole(Channel.SMS, Channel.EMAIL, Channel.MAIL)
            .withPropertyValues("app.security.allow-console-fallbacks=true")
            .run { context -> assertThat(context).hasNotFailed() }
    }

    /**
     * The reason the per-channel switches exist: an application with no SMS provider used to have
     * to set the blanket waiver, which disarmed the mail guards along with the SMS one.
     */
    @Test
    fun `waiving SMS leaves the mail guards armed`() {
        runnerWithConsole(Channel.SMS, Channel.MAIL)
            .withPropertyValues("app.security.console-fallbacks.allow-sms=true")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasStackTraceContaining("MailService is the console fallback")
                assertThat(stackTraceOf(context.startupFailure))
                    .doesNotContain("SmsService is the console fallback")
            }
    }

    @Test
    fun `an application that sends no SMS starts on the console fallback once SMS is waived`() {
        runnerWithConsole(Channel.SMS)
            .withPropertyValues("app.security.console-fallbacks.allow-sms=true")
            .run { context -> assertThat(context).hasNotFailed() }
    }

    @Test
    fun `email and mail are waived independently of each other`() {
        runnerWithConsole(Channel.EMAIL, Channel.MAIL)
            .withPropertyValues("app.security.console-fallbacks.allow-email=true")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasStackTraceContaining("MailService is the console fallback")
                assertThat(stackTraceOf(context.startupFailure))
                    .doesNotContain("EmailService is the console fallback")
            }

        runnerWithConsole(Channel.EMAIL, Channel.MAIL)
            .withPropertyValues(
                "app.security.console-fallbacks.allow-email=true",
                "app.security.console-fallbacks.allow-mail=true"
            )
            .run { context -> assertThat(context).hasNotFailed() }
    }

    @Test
    fun `a waiver does not excuse a dev-code override`() {
        runnerWithConsole(Channel.SMS)
            .withPropertyValues(
                "app.security.console-fallbacks.allow-sms=true",
                "app.auth.sms.dev-code=123456"
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("dev-code")
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
