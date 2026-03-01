package kz.innlab.template.config

import org.springframework.beans.factory.annotation.Value
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

private const val APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys"
private const val APPLE_ISSUER = "https://appleid.apple.com"

@Configuration
class AppleAuthConfig(
    @Value("\${app.auth.apple.bundle-id}")
    private val appleBundleId: String
) {

    @Bean(name = ["appleJwtDecoder"])
    fun appleJwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder
            .withJwkSetUri(APPLE_JWKS_URI)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()

        val audienceValidator: OAuth2TokenValidator<Jwt> =
            JwtClaimValidator(JwtClaimNames.AUD) { aud: List<String>? ->
                aud != null && aud.contains(appleBundleId)
            }

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(),          // validates exp (and nbf if present)
                JwtIssuerValidator(APPLE_ISSUER), // validates iss = "https://appleid.apple.com"
                audienceValidator                 // validates aud contains bundle ID
            )
        )

        return decoder
    }
}
