package kz.innlab.starter.shared.util

/**
 * `j***@example.com`: enough to tell log lines apart without writing the address out. Logs are
 * kept longer and read by more people than the data they describe.
 */
fun maskEmail(address: String): String {
    val at = address.indexOf('@')
    return if (at <= 0) "***" else "${address.first()}***${address.substring(at)}"
}
