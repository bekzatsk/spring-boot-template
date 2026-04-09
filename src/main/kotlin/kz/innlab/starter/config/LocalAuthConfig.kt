package kz.innlab.starter.config

import kz.innlab.starter.authentication.service.LocalUserDetailsService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class LocalAuthConfig {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder::class)
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /**
     * A dedicated AuthenticationManager for LOCAL email+password auth.
     * Kept separate from the resource server's JWT-based auth manager to avoid interference.
     */
    @Bean
    @ConditionalOnProperty(name = ["app.auth.local.enabled"], havingValue = "true", matchIfMissing = true)
    fun localAuthenticationManager(
        localUserDetailsService: LocalUserDetailsService,
        passwordEncoder: PasswordEncoder
    ): AuthenticationManager {
        val provider = DaoAuthenticationProvider(localUserDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return ProviderManager(provider)
    }
}
