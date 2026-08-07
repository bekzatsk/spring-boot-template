package kz.innlab.starter.authentication.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Every verification code in the starter — email, phone OTP, Telegram — comes from here, so a
 * defect is a weakness in all of them at once. Length and digit-only output matter because the
 * code is compared as a string, and the dev override exists to be a deliberate, visible bypass.
 */
class OneTimeCodesTest {

    @Test
    fun `generates a numeric code of the requested length`() {
        repeat(50) {
            val code = OneTimeCodes.generate(devCode = "", length = 6)
            assertThat(code).hasSize(6)
            assertThat(code).containsOnlyDigits()
        }
    }

    @Test
    fun `honours a non-default length`() {
        assertThat(OneTimeCodes.generate(devCode = "", length = 4)).hasSize(4)
        assertThat(OneTimeCodes.generate(devCode = "", length = 8)).hasSize(8)
    }

    @Test
    fun `keeps leading zeros instead of shortening the code`() {
        // Formatting a random int would drop them and hand out shorter codes now and then.
        val codes = (1..3_000).map { OneTimeCodes.generate(devCode = "", length = 6) }

        assertThat(codes).allMatch { it.length == 6 }
        assertThat(codes.any { it.startsWith("0") })
            .describedAs("expected at least one code starting with 0 in 3000 draws")
            .isTrue()
    }

    @Test
    fun `does not repeat itself`() {
        val codes = (1..500).map { OneTimeCodes.generate(devCode = "", length = 6) }.toSet()

        // 500 draws from a million values: collisions are possible but a near-constant
        // generator would collapse this set.
        assertThat(codes.size).isGreaterThan(450)
    }

    @Test
    fun `returns the dev override verbatim when one is set`() {
        assertThat(OneTimeCodes.generate(devCode = "123456", length = 6)).isEqualTo("123456")
        // The override wins regardless of the requested length — it is a fixed test value.
        assertThat(OneTimeCodes.generate(devCode = "42", length = 6)).isEqualTo("42")
    }

    @Test
    fun `a blank override does not disable generation`() {
        assertThat(OneTimeCodes.generate(devCode = "   ", length = 6)).hasSize(6)
    }
}
