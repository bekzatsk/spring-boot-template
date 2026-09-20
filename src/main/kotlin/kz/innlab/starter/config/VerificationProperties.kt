package kz.innlab.starter.config

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * One-time code settings. Dev-code overrides are separate because they gate different channels;
 * ProductionSafetyConfig refuses to start when any override is set under the prod profile.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
data class VerificationProperties(
    @field:Valid val verification: Channel = Channel(),
    @field:Valid val sms: Channel = Channel(),
    @field:Valid val phone: OtpChannel = OtpChannel(),
    @field:Valid val emailOtp: EmailOtpChannel = EmailOtpChannel()
) {
    @get:AssertTrue(message = "sms.dev-code must match phone.code-length and contain only digits")
    val supportedPhoneDevCode: Boolean
        get() = sms.devCode.isBlank() ||
            (sms.devCode.length == phone.codeLength && sms.devCode.all(Char::isDigit))

    @get:AssertTrue(message = "email-otp.dev-code must match email-otp.code-length and contain only digits")
    val supportedEmailOtpDevCode: Boolean
        get() = emailOtp.devCode.isBlank() ||
            (emailOtp.devCode.length == emailOtp.codeLength && emailOtp.devCode.all(Char::isDigit))

    data class Channel(
        /** Fixed code for local development. Must be blank in production. */
        val devCode: String = ""
    )

    data class OtpChannel(
        /** OTP length. Four and six digits are supported. */
        val codeLength: Int = 6
    ) {
        @get:AssertTrue(message = "code-length must be 4 or 6")
        val supportedCodeLength: Boolean
            get() = codeLength == 4 || codeLength == 6
    }

    data class EmailOtpChannel(
        /** OTP length. Four and six digits are supported. */
        val codeLength: Int = 6,
        /** Fixed code for local development. Must be blank in production. */
        val devCode: String = ""
    ) {
        @get:AssertTrue(message = "code-length must be 4 or 6")
        val supportedCodeLength: Boolean
            get() = codeLength == 4 || codeLength == 6
    }
}
