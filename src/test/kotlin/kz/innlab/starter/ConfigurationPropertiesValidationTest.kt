package kz.innlab.starter

import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.config.DeviceTokenProperties
import kz.innlab.starter.config.TelegramAuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

/**
 * These settings used to be scattered @Value injections with defaults repeated in the yaml and
 * in Kotlin default arguments, and nothing validated them. A nonsensical value (a negative token
 * lifetime, a zero attempt limit) has to fail at startup, not at the first request that uses it.
 */
class ConfigurationPropertiesValidationTest {

    @Configuration
    @EnableConfigurationProperties(
        AuthTokenProperties::class,
        TelegramAuthProperties::class,
        DeviceTokenProperties::class
    )
    class PropertiesConfig

    private fun runner() = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration::class.java))
        .withUserConfiguration(PropertiesConfig::class.java)

    @Test
    fun `defaults bind without any explicit configuration`() {
        runner().run { context ->
            assertThat(context).hasNotFailed()
            val tokens = context.getBean(AuthTokenProperties::class.java)
            assertThat(tokens.accessToken.expiryMinutes).isEqualTo(15)
            assertThat(tokens.refreshToken.expiryDays).isEqualTo(30)
            assertThat(context.getBean(DeviceTokenProperties::class.java).maxPerUser).isEqualTo(5)
        }
    }

    @Test
    fun `explicit values override the defaults`() {
        runner()
            .withPropertyValues(
                "app.auth.access-token.expiry-minutes=5",
                "app.auth.telegram.max-resends-per-session=7"
            )
            .run { context ->
                assertThat(context.getBean(AuthTokenProperties::class.java).accessToken.expiryMinutes).isEqualTo(5)
                assertThat(context.getBean(TelegramAuthProperties::class.java).maxResendsPerSession).isEqualTo(7)
            }
    }

    @Test
    fun `a negative access token lifetime fails startup`() {
        runner().withPropertyValues("app.auth.access-token.expiry-minutes=-5").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun `a zero attempt limit fails startup`() {
        runner().withPropertyValues("app.auth.telegram.max-attempts=0").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun `a zero device token cap fails startup`() {
        runner().withPropertyValues("app.notification.token.max-per-user=0").run { context ->
            assertThat(context).hasFailed()
        }
    }
}
