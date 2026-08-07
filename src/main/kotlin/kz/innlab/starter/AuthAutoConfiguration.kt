package kz.innlab.starter

import kz.innlab.starter.autoconfigure.AuthCoreAutoConfiguration
import kz.innlab.starter.autoconfigure.AuthProvidersAutoConfiguration
import kz.innlab.starter.autoconfigure.NotificationAutoConfiguration
import kz.innlab.starter.autoconfigure.UserAutoConfiguration
import kz.innlab.starter.config.AppleAuthConfig
import kz.innlab.starter.config.AsyncConfig
import kz.innlab.starter.config.AuthCookieProperties
import kz.innlab.starter.config.AuthFlywayConfig
import kz.innlab.starter.config.AuthSecurityProperties
import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.config.CorsProperties
import kz.innlab.starter.config.DeviceTokenProperties
import kz.innlab.starter.config.FirebaseConfig
import kz.innlab.starter.config.GoogleAuthConfig
import kz.innlab.starter.config.LocalAuthConfig
import kz.innlab.starter.config.MailConfig
import kz.innlab.starter.config.MailProperties
import kz.innlab.starter.config.RateLimitProperties
import kz.innlab.starter.config.NotificationConfig
import kz.innlab.starter.config.OpenApiConfig
import kz.innlab.starter.config.ProductionSafetyConfig
import kz.innlab.starter.config.RsaKeyConfig
import kz.innlab.starter.config.SecurityConfig
import kz.innlab.starter.config.SmsSchedulerConfig
import kz.innlab.starter.config.TelegramAuthProperties
import kz.innlab.starter.config.TelegramConfig
import kz.innlab.starter.config.TwilioConfig
import kz.innlab.starter.config.VerificationProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.context.annotation.Import

/**
 * Entry point of the starter.
 *
 * Beans are imported explicitly rather than discovered by a package scan. A scan inside an
 * auto-configuration registers everything unconditionally, which leaves a consumer application
 * no way to override an individual bean — the whole point of @ConditionalOnMissingBean. Every
 * bean now lives in one of the imported configurations and can be replaced by declaring one of
 * the same type.
 *
 * @AutoConfigurationPackage still points at the starter package so Spring Data finds the
 * repositories and JPA finds the entities.
 */
@AutoConfiguration(before = [ServletWebSecurityAutoConfiguration::class])
@AutoConfigurationPackage(basePackages = ["kz.innlab.starter"])
@EnableConfigurationProperties(
    AuthTokenProperties::class,
    VerificationProperties::class,
    TelegramAuthProperties::class,
    DeviceTokenProperties::class,
    RateLimitProperties::class,
    MailProperties::class,
    CorsProperties::class,
    AuthSecurityProperties::class,
    AuthCookieProperties::class
)
@Import(
    // feature beans
    AuthCoreAutoConfiguration::class,
    AuthProvidersAutoConfiguration::class,
    UserAutoConfiguration::class,
    NotificationAutoConfiguration::class,
    // infrastructure configurations
    RsaKeyConfig::class,
    SecurityConfig::class,
    AsyncConfig::class,
    AuthFlywayConfig::class,
    OpenApiConfig::class,
    ProductionSafetyConfig::class,
    SmsSchedulerConfig::class,
    NotificationConfig::class,
    MailConfig::class,
    FirebaseConfig::class,
    TwilioConfig::class,
    TelegramConfig::class,
    GoogleAuthConfig::class,
    AppleAuthConfig::class,
    LocalAuthConfig::class
)
class AuthAutoConfiguration
