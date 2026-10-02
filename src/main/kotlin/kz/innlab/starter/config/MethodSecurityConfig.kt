package kz.innlab.starter.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Turns on `@PreAuthorize` for the starter's admin-only handlers.
 *
 * The URL rules in [SecurityConfig] are not enough on their own: that chain is skipped when
 * `app.auth.security.enabled=false` or when the consumer defines its own, and Boot's fallback
 * chain only requires a login. Without a check on the handler itself, any signed-in user could
 * then reach the admin, inbox, mail-send and broadcast endpoints. This configuration is kept
 * apart from SecurityConfig so it stays active when that chain is off.
 *
 * Skipped when the consumer already enables method security (the interceptor bean exists), since
 * a second @EnableMethodSecurity would register the same beans twice.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(name = ["preAuthorizeAuthorizationMethodInterceptor"])
@EnableMethodSecurity
class MethodSecurityConfig
