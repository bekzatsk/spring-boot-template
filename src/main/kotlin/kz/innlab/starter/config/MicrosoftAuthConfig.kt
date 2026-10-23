package kz.innlab.starter.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@ConditionalOnProperty(name = ["app.auth.microsoft.enabled"], havingValue = "true")
class MicrosoftAuthConfig(
    private val properties: MicrosoftAuthProperties
) {

    /**
     * Pinned to one issuer — never the multi-tenant `common` endpoint, which accepts tokens from
     * every Microsoft tenant. Keys are fetched lazily, on the first login.
     */
    @Bean(name = ["microsoftJwtDecoder"])
    @ConditionalOnMissingBean(name = ["microsoftJwtDecoder"])
    fun microsoftJwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder
            .withJwkSetUri(properties.resolvedJwkSetUri())
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()

        val audienceValidator: OAuth2TokenValidator<Jwt> =
            JwtClaimValidator(JwtClaimNames.AUD) { aud: List<String>? ->
                aud != null && aud.contains(properties.clientId)
            }

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(),
                JwtIssuerValidator(properties.resolvedIssuer()),
                audienceValidator
            )
        )
        return decoder
    }
}
