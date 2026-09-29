package kz.innlab.starter

import kz.innlab.starter.config.JwtIdentityProperties
import kz.innlab.starter.config.RsaKeyConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ProductionJwtKeyTest {
    @Test
    fun `production refuses in memory JWT signing key`() {
        ApplicationContextRunner()
            .withInitializer { it.environment.setActiveProfiles("prod") }
            .withBean(JwtIdentityProperties::class.java, { JwtIdentityProperties("https://issuer.example", "api") })
            .withUserConfiguration(RsaKeyConfig::class.java)
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("Production JWT signing key is required")
            }
    }
}
