package kz.innlab.starter.authentication.service

/**
 * Every channel in `app.auth.otp.delivery-order` failed. Thrown to the caller in place of the last
 * channel's exception.
 *
 * Carries the channels tried, the class of each failure and the masked phone — and deliberately no
 * cause: a provider's exception echoes its request (Twilio's ApiException names the "To" number,
 * a message text holds the code), and the exception handler logs the whole chain with its stack
 * trace.
 */
class OtpDeliveryException(message: String) : RuntimeException(message)
