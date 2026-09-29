package kz.innlab.starter.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

@Configuration
class RsaKeyConfig(
    @Value("\${app.security.jwt.keystore-location:#{null}}")
    private val keystoreLocation: String?,
    @Value("\${app.security.jwt.keystore-password:#{null}}")
    private val keystorePassword: String?,
    @Value("\${app.security.jwt.key-alias:jwt}")
    private val keyAlias: String,
    private val jwtIdentityProperties: JwtIdentityProperties,
    private val environment: Environment
) {
    private val logger = LoggerFactory.getLogger(RsaKeyConfig::class.java)

    @Bean
    @ConditionalOnMissingBean(KeyPair::class)
    fun rsaKeyPair(): KeyPair {
        // Locals rather than !!: the Kotlin `spring` plugin makes this class open, so its
        // properties are non-final and cannot be smart-cast after the null check.
        val location = keystoreLocation
        val password = keystorePassword
        if (!location.isNullOrBlank() && !password.isNullOrBlank()) {
            logger.info("Loading RSA keypair from keystore: {}", location)
            val resource = DefaultResourceLoader().getResource(
                if (location.startsWith("classpath:") || location.startsWith("file:")) location
                else "classpath:$location"
            )
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(resource.inputStream, password.toCharArray())
            val privateKey = keyStore.getKey(keyAlias, password.toCharArray()) as RSAPrivateKey
            val publicKey = keyStore.getCertificate(keyAlias).publicKey as RSAPublicKey
            return KeyPair(publicKey, privateKey)
        }
        check(!environment.acceptsProfiles("prod")) {
            "Production JWT signing key is required: configure app.security.jwt.keystore-location and keystore-password"
        }
        logger.warn("No keystore configured — generating in-memory RSA keypair (NOT suitable for production)")
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    @Bean
    @ConditionalOnMissingBean(JWKSource::class)
    fun jwkSource(keyPair: KeyPair): JWKSource<SecurityContext> {
        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
            ))
            .build()
        return ImmutableJWKSet(JWKSet(rsaKey))
    }

    @Bean
    @ConditionalOnMissingBean(JwtEncoder::class)
    fun jwtEncoder(jwkSource: JWKSource<SecurityContext>): JwtEncoder =
        NimbusJwtEncoder(jwkSource)

    @Bean
    @ConditionalOnMissingBean(name = ["jwtDecoder"])
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build()
        val audienceValidator = JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { audience ->
            audience?.contains(jwtIdentityProperties.audience) == true
        }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator<Jwt>(
            JwtValidators.createDefaultWithIssuer(jwtIdentityProperties.issuer),
            audienceValidator
        ))
        return decoder
    }
}
