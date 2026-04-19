package kz.innlab.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.security")
data class AuthSecurityProperties(
    val enabled: Boolean = true,
    val publicPaths: List<String> = emptyList()
)
