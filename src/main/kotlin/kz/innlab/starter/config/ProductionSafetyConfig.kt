package kz.innlab.starter.config

import kz.innlab.starter.authentication.service.ConsoleSmsService
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.notification.service.ConsoleEmailService
import kz.innlab.starter.notification.service.ConsoleMailService
import kz.innlab.starter.notification.service.MailService
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Fail-fast guard against silently degraded production deployments.
 *
 * Refuses to start under the `prod` profile when:
 * - any `app.auth.*.dev-code` fixed-OTP override is set (account-takeover backdoor),
 * - Telegram auth is enabled without a webhook secret (webhook would accept forged updates),
 * - console fallback beans are serving SMS/email (verification codes would go to logs
 *   instead of users) — unless explicitly allowed via `app.security.allow-console-fallbacks=true`.
 */
@Configuration
@Profile("prod")
class ProductionSafetyConfig {

    @Bean
    fun productionSafetyGuard(
        smsService: ObjectProvider<SmsService>,
        emailService: ObjectProvider<EmailService>,
        mailService: ObjectProvider<MailService>,
        @Value("\${app.security.allow-console-fallbacks:false}") allowConsoleFallbacks: Boolean,
        @Value("\${app.auth.sms.dev-code:}") smsDevCode: String,
        @Value("\${app.auth.verification.dev-code:}") verificationDevCode: String,
        @Value("\${app.auth.telegram.dev-code:}") telegramDevCode: String,
        @Value("\${app.auth.telegram.enabled:false}") telegramEnabled: Boolean,
        @Value("\${app.auth.telegram.webhook-secret:}") telegramWebhookSecret: String
    ): InitializingBean = InitializingBean {
        val problems = mutableListOf<String>()

        if (smsDevCode.isNotBlank() || verificationDevCode.isNotBlank() || telegramDevCode.isNotBlank()) {
            problems += "a fixed OTP override (app.auth.*.dev-code) is set — this is an account-takeover " +
                "backdoor and must never be enabled in production"
        }
        if (telegramEnabled && telegramWebhookSecret.isBlank()) {
            problems += "app.auth.telegram.enabled=true but app.auth.telegram.webhook-secret is blank — " +
                "the webhook endpoint would accept forged updates from anyone"
        }
        if (!allowConsoleFallbacks) {
            if (smsService.ifAvailable is ConsoleSmsService) {
                problems += "SmsService is the console fallback — OTP codes would be logged instead of sent; " +
                    "configure a real provider (e.g. app.twilio.enabled=true) or set " +
                    "app.security.allow-console-fallbacks=true"
            }
            if (emailService.ifAvailable is ConsoleEmailService) {
                problems += "EmailService is the console fallback — verification codes would be logged instead " +
                    "of sent; configure mail (app.mail.enabled=true) or set " +
                    "app.security.allow-console-fallbacks=true"
            }
            if (mailService.ifAvailable is ConsoleMailService) {
                problems += "MailService is the console fallback — outgoing mail would be logged instead of " +
                    "sent; configure mail (app.mail.enabled=true) or set " +
                    "app.security.allow-console-fallbacks=true"
            }
        }

        check(problems.isEmpty()) {
            "Refusing to start with the 'prod' profile due to unsafe configuration:\n" +
                problems.joinToString("\n") { " - $it" }
        }
    }
}
