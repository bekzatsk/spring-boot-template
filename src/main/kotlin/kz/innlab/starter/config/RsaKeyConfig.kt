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
import org.springframework.core.io.ClassPathResource
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

@Configuration
class RsaKeyConfig(
    @Value("\${app.security.jwt.keystore-location:#{null}}")
    private val keystoreLocation: String?,
    @Value("\${app.security.jwt.keystore-password:#{null}}")
    private val keystorePassword: String?,
    @Value("\${app.security.jwt.key-alias:jwt}")
    private val keyAlias: String
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
            val resource = ClassPathResource(location)
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(resource.inputStream, password.toCharArray())
            val privateKey = keyStore.getKey(keyAlias, password.toCharArray()) as RSAPrivateKey
            val publicKey = keyStore.getCertificate(keyAlias).publicKey as RSAPublicKey
            return KeyPair(publicKey, privateKey)
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
            .keyID(UUID.randomUUID().toString())
            .build()
        return ImmutableJWKSet(JWKSet(rsaKey))
    }

    @Bean
    @ConditionalOnMissingBean(JwtEncoder::class)
    fun jwtEncoder(jwkSource: JWKSource<SecurityContext>): JwtEncoder =
        NimbusJwtEncoder(jwkSource)

    @Bean
    @ConditionalOnMissingBean(name = ["jwtDecoder"])
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        NimbusJwtDecoder.withJwkSource(jwkSource).build()
}
