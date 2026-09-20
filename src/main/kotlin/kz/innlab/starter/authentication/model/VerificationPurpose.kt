package kz.innlab.starter.authentication.model

enum class VerificationPurpose {
    FORGOT_PASSWORD,
    CHANGE_EMAIL,
    CHANGE_PHONE,
    VERIFY_EMAIL,

    /** Passwordless email OTP login. */
    EMAIL_LOGIN,

    /** Phone OTP login. Previously lived in its own sms_verifications table. */
    PHONE_LOGIN
}
