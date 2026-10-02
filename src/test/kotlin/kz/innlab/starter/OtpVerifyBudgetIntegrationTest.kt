package kz.innlab.starter

import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.VerificationCodeService
import kz.innlab.starter.shared.ratelimit.RateLimitExceededException
import kz.innlab.starter.shared.ratelimit.RateLimiter
import kz.innlab.starter.notification.service.PushService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * The per-code attempt counter resets with every new code. The cross-code budget is what stops
 * an attacker from requesting a fresh code each minute and guessing forever.
 */
@SpringBootTest(
    properties = [
        "app.auth.rate-limit.otp-verify.max-attempts=4",
        "app.auth.rate-limit.otp-verify.window-seconds=3600"
    ]
)
class OtpVerifyBudgetIntegrationTest {

    @MockitoBean private lateinit var pushService: PushService
    @Autowired private lateinit var verificationCodeService: VerificationCodeService
    @Autowired private lateinit var verificationCodeRepository: VerificationCodeRepository
    @Autowired private lateinit var rateLimiter: RateLimiter

    private val email = "budget@example.com"
    private val purpose = VerificationPurpose.EMAIL_LOGIN

    @BeforeEach
    fun setUp() {
        verificationCodeRepository.deleteAll()
        rateLimiter.reset("otp-verify:$purpose:$email")
    }

    /** Issues a fresh code, skipping the resend cooldown the way a minute of waiting would. */
    private fun freshCode() = verificationCodeRepository.deleteAll()
        .let { verificationCodeService.createCode(email, purpose) }

    private fun wrong(code: String) = if (code == "000000") "111111" else "000000"

    @Test
    fun `new codes do not reset the guessing budget`() {
        val first = freshCode()
        repeat(3) {
            assertThatThrownBy { verificationCodeService.verifyCode(first.verificationId, email, purpose, wrong(first.code)) }
                .isInstanceOf(BadCredentialsException::class.java)
        }

        val second = freshCode()
        assertThatThrownBy { verificationCodeService.verifyCode(second.verificationId, email, purpose, wrong(second.code)) }
            .isInstanceOf(BadCredentialsException::class.java)

        // Budget of 4 spent: even the right code is refused until the window passes.
        assertThatThrownBy { verificationCodeService.verifyCode(second.verificationId, email, purpose, second.code) }
            .isInstanceOf(RateLimitExceededException::class.java)
    }

    @Test
    fun `a correct code clears the budget`() {
        val first = freshCode()
        repeat(2) {
            assertThatThrownBy { verificationCodeService.verifyCode(first.verificationId, email, purpose, wrong(first.code)) }
                .isInstanceOf(BadCredentialsException::class.java)
        }
        verificationCodeService.verifyCode(first.verificationId, email, purpose, first.code)

        val second = freshCode()
        repeat(3) {
            assertThatThrownBy { verificationCodeService.verifyCode(second.verificationId, email, purpose, wrong(second.code)) }
                .isInstanceOf(BadCredentialsException::class.java)
        }
        // 3 attempts since the reset, within the budget of 4: a fresh code still works.
        val third = freshCode()
        verificationCodeService.verifyCode(third.verificationId, email, purpose, third.code)
    }
}
