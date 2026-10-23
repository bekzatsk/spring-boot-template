package kz.innlab.starter.authentication.service

/**
 * Subject and body of a one-time-code email. The mail services used to build them inline from
 * the purpose name, so recipients read "EMAIL_LOGIN Verification Code" — an internal identifier,
 * in English only.
 *
 * Declare a bean of this type to replace the wording or translate it. [purpose] is one of
 * EMAIL_LOGIN, VERIFY_EMAIL, FORGOT_PASSWORD, CHANGE_EMAIL, REAUTH; treat unknown values as a
 * generic code, since later versions may add purposes.
 */
interface VerificationEmailMessages {

    fun compose(purpose: String, code: String): VerificationEmail
}

/** [html] is optional: without it the email is sent as plain text. */
data class VerificationEmail(
    val subject: String,
    val text: String,
    val html: String? = null
)

/** Brand-neutral English defaults. */
class DefaultVerificationEmailMessages : VerificationEmailMessages {

    override fun compose(purpose: String, code: String): VerificationEmail {
        val (subject, intro) = when (purpose) {
            "EMAIL_LOGIN" -> "Your sign-in code" to "Use this code to sign in:"
            "VERIFY_EMAIL" -> "Confirm your email address" to "Use this code to confirm your email address:"
            "FORGOT_PASSWORD" -> "Reset your password" to "Use this code to reset your password:"
            "CHANGE_EMAIL" -> "Confirm your new email address" to "Use this code to confirm your new email address:"
            "REAUTH" -> "Confirm it's you" to "Use this code to confirm it's you:"
            else -> "Your verification code" to "Your verification code:"
        }
        val ignore = "If you did not request this code, ignore this email."
        return VerificationEmail(
            subject = subject,
            text = "$intro $code\n\n$ignore",
            html = """
                <!DOCTYPE html>
                <html><body style="font-family:sans-serif;color:#222">
                <p>${escape(intro)}</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:4px">${escape(code)}</p>
                <p style="color:#666">${escape(ignore)}</p>
                </body></html>
            """.trimIndent()
        )
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
