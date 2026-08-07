package kz.innlab.starter.authentication.service

/**
 * Text the login bot sends. Split out of TelegramAuthService, which had the copy inline and
 * named a specific product ("MathHub", "mathhub.kz") — every consumer of this starter was
 * messaging its users under someone else's brand.
 *
 * Declare a bean of this type to replace the wording or translate it.
 */
interface TelegramBotMessages {

    fun sessionExpired(): String

    fun tooManyRequests(): String

    fun verificationCode(code: String, ttlMinutes: Long): String

    fun resentVerificationCode(code: String, ttlMinutes: Long): String

    fun welcome(): String

    fun help(): String
}

/** Brand-neutral Kazakh defaults. */
class DefaultTelegramBotMessages : TelegramBotMessages {

    override fun sessionExpired(): String =
        "Сессия мерзімі өтіп кетті. Сайтта қайтадан бастаңыз."

    override fun tooManyRequests(): String =
        "Тым көп сұраныс. Кейінірек қайтадан көріңіз."

    override fun verificationCode(code: String, ttlMinutes: Long): String =
        "🔐 Сіздің растау кодыңыз: $code\n\n⏰ Код $ttlMinutes минут жарамды."

    override fun resentVerificationCode(code: String, ttlMinutes: Long): String =
        "🔐 Сіздің жаңа растау кодыңыз: $code\n\n⏰ Код $ttlMinutes минут жарамды."

    override fun welcome(): String =
        "Қош келдіңіз! Тіркелу үшін сайтқа өтіңіз."

    override fun help(): String =
        "Тіркелу үшін сайтқа кіріңіз және \"Telegram арқылы тіркелу\" батырмасын басыңыз."
}
