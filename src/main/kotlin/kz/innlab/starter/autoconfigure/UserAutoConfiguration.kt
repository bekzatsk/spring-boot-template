package kz.innlab.starter.autoconfigure

import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.user.controller.AdminUserController
import kz.innlab.starter.user.controller.UserController
import kz.innlab.starter.user.repository.AdminAuditLogRepository
import kz.innlab.starter.user.repository.UserRepository
import kz.innlab.starter.user.service.AdminUserService
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
        authTokenProperties: AuthTokenProperties
    ): UserService = UserService(userRepository, passwordEncoder, authTokenProperties)

    @Bean
    @ConditionalOnMissingBean
    fun adminUserService(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        refreshTokenRevoker: RefreshTokenRevoker,
        auditLogRepository: AdminAuditLogRepository
    ): AdminUserService = AdminUserService(userRepository, passwordEncoder, refreshTokenRevoker, auditLogRepository)

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
