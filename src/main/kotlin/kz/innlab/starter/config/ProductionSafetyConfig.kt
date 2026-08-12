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
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Fail-fast guard against silently degraded production deployments.
 *
 * Refuses to start under the `prod` profile when:
 * - any `app.auth.*.dev-code` fixed-OTP override is set (account-takeover backdoor),
 * - Telegram auth is enabled without a webhook secret (webhook would accept forged updates),
 * - a console fallback bean is serving a channel the application actually uses (verification codes
 *   would go to the log instead of to users).
 *
 * The console-fallback checks are waived per channel through
 * [ConsoleFallbackProperties], because an application typically uses some channels and not others.
 * `app.security.allow-console-fallbacks=true` still waives all three at once and is kept for
 * compatibility, but prefer the per-channel switches: a blanket waiver set to get past a missing
 * SMS provider also disarms the mail guards.
 *
 * Whether a channel is used is not derivable from configuration — the `change-phone` endpoints send
 * an OTP whether or not the phone provider is enabled, and any consumer can call `MailService`
 * directly — so the waiver is a statement by the operator, not a guess by the starter.
 */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(ConsoleFallbackProperties::class)
class ProductionSafetyConfig {

    @Bean
    fun productionSafetyGuard(
        smsService: ObjectProvider<SmsService>,
        emailService: ObjectProvider<EmailService>,
        mailService: ObjectProvider<MailService>,
        consoleFallbacks: ConsoleFallbackProperties,
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
        if (!allowConsoleFallbacks && !consoleFallbacks.allowSms && smsService.ifAvailable is ConsoleSmsService) {
            problems += "SmsService is the console fallback — OTP codes for phone login and " +
                "/users/me/change-phone would be logged instead of sent; configure a real provider " +
                "(e.g. app.twilio.enabled=true), or set app.security.console-fallbacks.allow-sms=true " +
                "if this application sends no SMS"
        }
        if (!allowConsoleFallbacks && !consoleFallbacks.allowEmail && emailService.ifAvailable is ConsoleEmailService) {
            problems += "EmailService is the console fallback — verification codes for registration, " +
                "password reset and email change would be logged instead of sent; configure mail " +
                "(app.mail.enabled=true), or set app.security.console-fallbacks.allow-email=true if this " +
                "application sends no verification email"
        }
        if (!allowConsoleFallbacks && !consoleFallbacks.allowMail && mailService.ifAvailable is ConsoleMailService) {
            problems += "MailService is the console fallback — outgoing mail from /api/v1/mail would be " +
                "logged instead of sent; configure mail (app.mail.enabled=true), or set " +
                "app.security.console-fallbacks.allow-mail=true if this application sends no mail"
        }

        check(problems.isEmpty()) {
            "Refusing to start with the 'prod' profile due to unsafe configuration:\n" +
                problems.joinToString("\n") { " - $it" }
        }
    }
}
