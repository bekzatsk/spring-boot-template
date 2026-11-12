package kz.innlab.starter.shared.util

/**
 * `j***@example.com`: enough to tell log lines apart without writing the address out. Logs are
 * kept longer and read by more people than the data they describe.
 */
fun maskEmail(address: String): String {
    val at = address.indexOf('@')
    return if (at <= 0) "***" else "${address.first()}***${address.substring(at)}"
}

/** `+77***67`: country code and the last two digits, nothing that identifies the subscriber. */
fun maskPhone(phone: String): String =
    if (phone.length <= 4) "***" else phone.take(3) + "***" + phone.takeLast(2)
