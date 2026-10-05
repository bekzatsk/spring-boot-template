package kz.innlab.starter.autoconfigure

import kz.innlab.starter.shared.security.FreshLoginGuard
import kz.innlab.starter.config.AuthSecurityProperties
import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.user.controller.AdminUserController
import kz.innlab.starter.user.controller.UserController
import kz.innlab.starter.user.repository.AdminAuditLogRepository
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.user.service.AdminUserService
import kz.innlab.starter.user.service.DeviceRegistrationRevoker
import kz.innlab.starter.user.service.RefreshTokenRevoker
import kz.innlab.starter.user.service.UserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

/** User profile and admin user management. */
@Configuration(proxyBeanMethods = false)
class UserAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun userService(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        authTokenProperties: AuthTokenProperties,
        refreshTokenRevoker: RefreshTokenRevoker,
        deviceRegistrationRevoker: DeviceRegistrationRevoker,
        freshLoginGuard: FreshLoginGuard
    ): UserService = UserService(
        userRepository, passwordEncoder, authTokenProperties, refreshTokenRevoker, deviceRegistrationRevoker,
        freshLoginGuard
    )

    @Bean
    @ConditionalOnMissingBean
    fun adminUserService(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        refreshTokenRevoker: RefreshTokenRevoker,
        auditLogRepository: AdminAuditLogRepository,
        userService: UserService,
        freshLoginGuard: FreshLoginGuard
    ): AdminUserService = AdminUserService(
        userRepository, passwordEncoder, refreshTokenRevoker, auditLogRepository, userService, freshLoginGuard
    )

    @Bean
    @ConditionalOnMissingBean
    fun freshLoginGuard(authSecurityProperties: AuthSecurityProperties): FreshLoginGuard =
        FreshLoginGuard(authSecurityProperties)

    @Bean
    @ConditionalOnMissingBean
    fun userController(userService: UserService): UserController = UserController(userService)

    @Bean
    @ConditionalOnMissingBean
    fun adminUserController(
        userService: UserService,
        adminUserService: AdminUserService
    ): AdminUserController = AdminUserController(userService, adminUserService)
}
