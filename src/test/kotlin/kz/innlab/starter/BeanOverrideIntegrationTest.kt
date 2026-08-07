package kz.innlab.starter

import kz.innlab.starter.authentication.service.AuthTokenIssuer
import kz.innlab.starter.authentication.service.RefreshTokenService
import kz.innlab.starter.authentication.service.TokenService
import kz.innlab.starter.notification.service.MailSendPolicy
import kz.innlab.starter.notification.service.PushService
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.user.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * The reason the package scan was removed: a scan registers everything unconditionally, so a
 * consumer application had no way to substitute its own implementation of a starter bean. Each
 * bean is now declared with @ConditionalOnMissingBean, so a consumer-declared bean of the same
 * type wins.
 *
 * AuthTokenIssuer stands in for the general case — it is an ordinary internal service, not one
 * of the few interfaces (SmsService, EmailService, PushService) that were already replaceable.
 */
@SpringBootTest
@Import(BeanOverrideIntegrationTest.ConsumerOverrides::class)
class BeanOverrideIntegrationTest {

    class RecordingAuthTokenIssuer(
        tokenService: TokenService,
        refreshTokenService: RefreshTokenService
    ) : AuthTokenIssuer(tokenService, refreshTokenService) {

        var issuedFor: MutableList<User> = mutableListOf()

        override fun issue(user: User): AuthResponse {
            issuedFor.add(user)
            return super.issue(user)
        }
    }

    @TestConfiguration
    class ConsumerOverrides {
        @Bean
        fun authTokenIssuer(
            tokenService: TokenService,
            refreshTokenService: RefreshTokenService
        ): AuthTokenIssuer = RecordingAuthTokenIssuer(tokenService, refreshTokenService)
    }

    @MockitoBean
    private lateinit var pushService: PushService

    @Autowired
    private lateinit var authTokenIssuer: AuthTokenIssuer

    @Autowired
    private lateinit var mailSendPolicy: MailSendPolicy

    @Test
    fun `a consumer-declared bean replaces the starter's own`() {
        assertThat(authTokenIssuer)
            .describedAs("starter bean must yield to the consumer's @Bean")
            .isInstanceOf(RecordingAuthTokenIssuer::class.java)
    }

    @Test
    fun `beans the consumer did not override still come from the starter`() {
        assertThat(mailSendPolicy).isNotNull()
    }
}
