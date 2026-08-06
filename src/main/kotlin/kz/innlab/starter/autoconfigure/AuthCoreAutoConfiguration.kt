package kz.innlab.starter.autoconfigure

import tools.jackson.databind.ObjectMapper
import kz.innlab.starter.authentication.controller.AccountManagementController
import kz.innlab.starter.authentication.controller.AuthController
import kz.innlab.starter.authentication.cookie.AuthCookieWriter
import kz.innlab.starter.authentication.exception.AuthExceptionHandler
import kz.innlab.starter.authentication.filter.ApiAccessDeniedHandler
import kz.innlab.starter.authentication.filter.ApiAuthenticationEntryPoint
import kz.innlab.starter.authentication.filter.RequiredActionFilter
import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.AccountManagementService
import kz.innlab.starter.authentication.service.AuthTokenIssuer
import kz.innlab.starter.authentication.service.EmailService
import kz.innlab.starter.authentication.service.OtpDeliveryService
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.SmsService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.authentication.service.VerificationAttemptRecorder
import kz.innlab.starter.authentication.service.VerificationCodeService
import kz.innlab.starter.authentication.service.WhatsAppService
import kz.innlab.starter.authentication.cookie.AuthResponseCookieAdvice
import kz.innlab.starter.config.AuthSecurityProperties
import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.config.VerificationProperties
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.user.repository.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import java.util.Optional

/**
 * Core authentication beans — always registered.
 *
 * Every bean is declared explicitly with @ConditionalOnMissingBean instead of being picked up by
 * a package scan, so a consumer application can replace any one of them by declaring its own.
 * Method names are the bean names and must stay stable: @Qualifier and
 * @ConditionalOnMissingBean(name = …) resolve by name here and in consumer code
 * (AutoConfigurationContractTest pins them).
 *
 * The classes themselves keep their @Service/@Component annotations even though nothing scans
 * for them: the Kotlin `spring` compiler plugin uses those annotations to make the classes open,
 * and a class carrying @Transactional only on its methods would otherwise be final and could not
 * be proxied.
 */
@Configuration(proxyBeanMethods = false)
class AuthCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun afterCommitRunner(): AfterCommitRunner = AfterCommitRunner()

    @Bean
    @ConditionalOnMissingBean
    fun tokenService(jwtEncoder: JwtEncoder, authTokenProperties: AuthTokenProperties): TokenService =
        TokenService(jwtEncoder, authTokenProperties)

    @Bean
    @ConditionalOnMissingBean
    fun refreshTokenService(
        refreshTokenRepository: RefreshTokenRepository,
        authTokenProperties: AuthTokenProperties
    ): RefreshTokenService = RefreshTokenService(refreshTokenRepository, authTokenProperties)

    @Bean
    @ConditionalOnMissingBean
    fun authTokenIssuer(tokenService: TokenService, refreshTokenService: RefreshTokenService): AuthTokenIssuer =
        AuthTokenIssuer(tokenService, refreshTokenService)

    @Bean
    @ConditionalOnMissingBean
    fun verificationAttemptRecorder(
        verificationCodeRepository: VerificationCodeRepository
    ): VerificationAttemptRecorder = VerificationAttemptRecorder(verificationCodeRepository)

    @Bean
    @ConditionalOnMissingBean
    fun verificationCodeService(
        verificationCodeRepository: VerificationCodeRepository,
        attemptRecorder: VerificationAttemptRecorder,
        passwordEncoder: PasswordEncoder,
        verificationProperties: VerificationProperties
    ): VerificationCodeService =
        VerificationCodeService(verificationCodeRepository, attemptRecorder, passwordEncoder, verificationProperties)

    @Bean
    @ConditionalOnMissingBean
    fun otpDeliveryService(
        whatsAppService: Optional<WhatsAppService>,
        smsService: SmsService
    ): OtpDeliveryService = OtpDeliveryService(whatsAppService, smsService)

    @Bean
    @ConditionalOnMissingBean
    fun accountManagementService(
        userRepository: UserRepository,
        verificationCodeService: VerificationCodeService,
        emailService: EmailService,
        otpDeliveryService: OtpDeliveryService,
        passwordEncoder: PasswordEncoder,
        refreshTokenRepository: RefreshTokenRepository
    ): AccountManagementService = AccountManagementService(
        userRepository, verificationCodeService, emailService,
        otpDeliveryService, passwordEncoder, refreshTokenRepository
    )

    // --- web layer ---

    @Bean
    @ConditionalOnMissingBean
    fun authExceptionHandler(): AuthExceptionHandler = AuthExceptionHandler()

    @Bean
    @ConditionalOnMissingBean
    fun apiAccessDeniedHandler(objectMapper: ObjectMapper): ApiAccessDeniedHandler =
        ApiAccessDeniedHandler(objectMapper)

    @Bean
    @ConditionalOnMissingBean
    fun apiAuthenticationEntryPoint(objectMapper: ObjectMapper): ApiAuthenticationEntryPoint =
        ApiAuthenticationEntryPoint(objectMapper)

    @Bean
    @ConditionalOnMissingBean
    fun requiredActionFilter(
        properties: AuthSecurityProperties,
        // RequiredActionFilter still writes with Jackson 2 while the rest of the starter is on
        // Jackson 3; both ObjectMapper beans exist, so the parameter type picks the right one.
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ): RequiredActionFilter = RequiredActionFilter(properties, objectMapper)

    @Bean
    @ConditionalOnMissingBean
    fun authResponseCookieAdvice(cookieWriter: ObjectProvider<AuthCookieWriter>): AuthResponseCookieAdvice =
        AuthResponseCookieAdvice(cookieWriter)

    @Bean
    @ConditionalOnMissingBean
    fun authController(
        refreshTokenService: RefreshTokenService,
        tokenService: TokenService,
        cookieWriter: ObjectProvider<AuthCookieWriter>
    ): AuthController = AuthController(refreshTokenService, tokenService, cookieWriter)

    @Bean
    @ConditionalOnMissingBean
    fun accountManagementController(
        accountManagementService: AccountManagementService
    ): AccountManagementController = AccountManagementController(accountManagementService)
}
