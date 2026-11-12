package kz.innlab.starter.authentication.service

/**
 * One way of getting a one-time code to a phone: Telegram Gateway, WhatsApp, SMS, or one of the
 * application's own.
 *
 * [OtpDeliveryService] tries the channels in the order of `app.auth.otp.delivery-order`, matched
 * by [name]. An implementation MUST throw a RuntimeException when the code was not accepted for
 * delivery — that is what moves delivery on to the next channel.
 *
 * Every bean of this type is a channel. `SmsService` and `WhatsAppService` beans become the
 * `sms` and `whatsapp` channels without implementing this interface; a bean of this type named
 * `sms` or `whatsapp` replaces them.
 */
interface OtpDeliveryChannel {
    /** The name used in `app.auth.otp.delivery-order`, compared without case. */
    val name: String

    fun sendCode(phone: String, code: String)
}
