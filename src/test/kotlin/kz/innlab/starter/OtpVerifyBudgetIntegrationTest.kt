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

    @Test
    fun `case variants of an email share one code and one budget`() {
        val reset = VerificationPurpose.FORGOT_PASSWORD
        rateLimiter.reset("otp-verify:$reset:case@example.com")
        val issued = verificationCodeService.createCode("Case@Example.com", reset)

        // A second variant within the minute hits the same cooldown instead of a fresh code.
        assertThatThrownBy { verificationCodeService.createCode("CASE@EXAMPLE.COM", reset) }
            .isInstanceOf(IllegalStateException::class.java)

        // Guesses through different variants all count against the one budget of 4.
        listOf("CASE@example.com", "case@EXAMPLE.com", "Case@example.COM").forEach { variant ->
            assertThatThrownBy { verificationCodeService.verifyCode(issued.verificationId, variant, reset, wrong(issued.code)) }
                .isInstanceOf(BadCredentialsException::class.java)
        }
        assertThatThrownBy { verificationCodeService.verifyCode(issued.verificationId, "cAsE@example.com", reset, wrong(issued.code)) }
            .isInstanceOf(BadCredentialsException::class.java)
        assertThatThrownBy { verificationCodeService.verifyCode(issued.verificationId, "case@example.com", reset, issued.code) }
            .isInstanceOf(RateLimitExceededException::class.java)
    }
}
