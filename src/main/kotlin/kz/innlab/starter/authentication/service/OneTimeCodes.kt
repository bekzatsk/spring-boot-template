package kz.innlab.starter.authentication.service

import java.security.SecureRandom

/**
 * Single source of numeric one-time codes for all channels (email, SMS, Telegram).
 * Previously each service kept its own copy of the generation logic, which had already
 * started to drift between channels.
 */
object OneTimeCodes {

    private val random = SecureRandom()

    fun generate(devCode: String, length: Int = 6): String {
        if (devCode.isNotBlank()) return devCode
        return buildString(length) {
            repeat(length) { append(random.nextInt(10)) }
        }
    }
}
