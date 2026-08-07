package kz.innlab.starter.autoconfigure

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import kz.innlab.starter.authentication.controller.AppleAuthController
import kz.innlab.starter.authentication.controller.GoogleAuthController
import kz.innlab.starter.authentication.controller.LocalAuthController
import kz.innlab.starter.authentication.controller.PhoneAuthController
import kz.innlab.starter.authentication.controller.TelegramAuthController
import kz.innlab.starter.authentication.controller.TelegramWebhookController
import kz.innlab.starter.authentication.repository.TelegramAuthSessionRepository
import kz.innlab.starter.authentication.service.AccountManagementService
import kz.innlab.starter.authentication.service.AppleOAuth2Service
import kz.innlab.starter.authentication.service.AuthTokenIssuer
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.GoogleOAuth2Service
import kz.innlab.starter.authentication.service.LocalAuthService
import kz.innlab.starter.authentication.service.LocalUserDetailsService
import kz.innlab.starter.authentication.service.OtpDeliveryService
import kz.innlab.starter.authentication.service.PhoneOtpService
import kz.innlab.starter.authentication.service.TelegramAuthService
import kz.innlab.starter.authentication.service.DefaultTelegramBotMessages
import kz.innlab.starter.authentication.service.TelegramBotMessages
import kz.innlab.starter.authentication.service.TelegramBotService
import kz.innlab.starter.authentication.service.VerificationCodeService
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.config.RateLimitProperties
import kz.innlab.starter.shared.ratelimit.RateLimiter
import kz.innlab.starter.config.TelegramAuthProperties
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.user.service.UserService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder

/**
 * One nested configuration per login provider, each gated by the same property that used to gate
 * the scanned classes. Turning a provider off removes its service and its endpoints together.
 */
@Configuration(proxyBeanMethods = false)
class AuthProvidersAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["app.auth.local.enabled"], havingValue = "true", matchIfMissing = true)
    class LocalAuth {

        @Bean
        @ConditionalOnMissingBean
        fun localUserDetailsService(userRepository: UserRepository): LocalUserDetailsService =
            LocalUserDetailsService(userRepository)

        @Bean
        @ConditionalOnMissingBean
        fun localAuthService(
            @Qualifier("localAuthenticationManager") authenticationManager: AuthenticationManager,
            userRepository: UserRepository,
            passwordEncoder: PasswordEncoder,
            authTokenIssuer: AuthTokenIssuer,
            verificationCodeService: VerificationCodeService,
            emailService: EmailService,
            authTokenProperties: AuthTokenProperties,
            rateLimiter: RateLimiter,
            rateLimitProperties: RateLimitProperties
        ): LocalAuthService = LocalAuthService(
            authenticationManager, userRepository, passwordEncoder, authTokenIssuer,
            verificationCodeService, emailService, authTokenProperties, rateLimiter, rateLimitProperties
        )

        @Bean
        @ConditionalOnMissingBean
        fun localAuthController(
            localAuthService: LocalAuthService,
            accountManagementService: AccountManagementService
        ): LocalAuthController = LocalAuthController(localAuthService, accountManagementService)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["app.auth.google.enabled"], havingValue = "true")
    class GoogleAuth {

        @Bean
        @ConditionalOnMissingBean
        fun googleOAuth2Service(
            googleIdTokenVerifier: GoogleIdTokenVerifier,
            userService: UserService,
            authTokenIssuer: AuthTokenIssuer
        ): GoogleOAuth2Service = GoogleOAuth2Service(googleIdTokenVerifier, userService, authTokenIssuer)

        @Bean
        @ConditionalOnMissingBean
        fun googleAuthController(googleOAuth2Service: GoogleOAuth2Service): GoogleAuthController =
            GoogleAuthController(googleOAuth2Service)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["app.auth.apple.enabled"], havingValue = "true")
    class AppleAuth {

        @Bean
        @ConditionalOnMissingBean
        fun appleOAuth2Service(
            @Qualifier("appleJwtDecoder") appleJwtDecoder: JwtDecoder,
            userService: UserService,
            authTokenIssuer: AuthTokenIssuer
        ): AppleOAuth2Service = AppleOAuth2Service(appleJwtDecoder, userService, authTokenIssuer)

        @Bean
        @ConditionalOnMissingBean
        fun appleAuthController(appleOAuth2Service: AppleOAuth2Service): AppleAuthController =
            AppleAuthController(appleOAuth2Service)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["app.auth.phone.enabled"], havingValue = "true", matchIfMissing = true)
    class PhoneAuth {

        @Bean
        @ConditionalOnMissingBean
        fun phoneOtpService(
            verificationCodeService: VerificationCodeService,
            otpDeliveryService: OtpDeliveryService,
            userService: UserService,
            authTokenIssuer: AuthTokenIssuer,
            afterCommitRunner: AfterCommitRunner
        ): PhoneOtpService = PhoneOtpService(
            verificationCodeService, otpDeliveryService, userService, authTokenIssuer, afterCommitRunner
        )

        @Bean
        @ConditionalOnMissingBean
        fun phoneAuthController(phoneOtpService: PhoneOtpService): PhoneAuthController =
            PhoneAuthController(phoneOtpService)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["app.auth.telegram.enabled"], havingValue = "true")
    class TelegramAuth {

        @Bean
        @ConditionalOnMissingBean
        fun telegramBotMessages(): TelegramBotMessages = DefaultTelegramBotMessages()

        @Bean
        @ConditionalOnMissingBean
        fun telegramAuthService(
            sessionRepository: TelegramAuthSessionRepository,
            telegramBotService: TelegramBotService,
            userService: UserService,
            authTokenIssuer: AuthTokenIssuer,
            passwordEncoder: PasswordEncoder,
            afterCommitRunner: AfterCommitRunner,
            messages: TelegramBotMessages,
            telegramProperties: TelegramAuthProperties
        ): TelegramAuthService = TelegramAuthService(
            sessionRepository, telegramBotService, userService, authTokenIssuer,
            passwordEncoder, afterCommitRunner, messages, telegramProperties
        )

        @Bean
        @ConditionalOnMissingBean
        fun telegramAuthController(
            telegramAuthService: TelegramAuthService,
            telegramProperties: TelegramAuthProperties
        ): TelegramAuthController = TelegramAuthController(telegramAuthService, telegramProperties)

        @Bean
        @ConditionalOnMissingBean
        fun telegramWebhookController(
            telegramAuthService: TelegramAuthService,
            telegramProperties: TelegramAuthProperties
        ): TelegramWebhookController = TelegramWebhookController(telegramAuthService, telegramProperties)
    }
}
