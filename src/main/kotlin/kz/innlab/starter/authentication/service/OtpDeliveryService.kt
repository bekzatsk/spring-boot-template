package kz.innlab.starter.authentication.service

import kz.innlab.starter.config.OtpDeliveryProperties
import kz.innlab.starter.shared.util.maskPhone
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.Optional

/**
 * High-level OTP delivery orchestrator.
 *
 * Tries the channels named in `app.auth.otp.delivery-order` (default `[whatsapp, sms]`, the
 * behaviour from before the order was configurable) one after another. A RuntimeException from a
 * channel moves on to the next one; when the last one fails too, [OtpDeliveryException] reaches the
 * caller (500 through the exception handler). It names the channels and exception classes, never
 * the provider's message, which can carry the code and the full number.
 *
 * Channels:
 *  - `whatsapp` — the WhatsAppService bean, if there is one (Optional, absent by default);
 *  - `sms` — the SmsService bean (always present: ConsoleSmsService is the default);
 *  - every [OtpDeliveryChannel] bean under its own name — the Telegram Gateway is `telegram`.
 *    One named `sms` or `whatsapp` replaces the built-in channel.
 *
 * A listed channel without a bean is skipped. An order that leaves no channel at all fails at
 * construction, i.e. at startup — not at the first login.
 */
@Service
class OtpDeliveryService(
    whatsAppService: Optional<WhatsAppService>,
    smsService: SmsService,
    channels: List<OtpDeliveryChannel>,
    properties: OtpDeliveryProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OtpDeliveryService::class.java)

        const val WHATSAPP = "whatsapp"
        const val SMS = "sms"

        private fun key(name: String) = name.trim().lowercase(Locale.ROOT)
    }

    private class Adapter(override val name: String, private val send: (String, String) -> Unit) : OtpDeliveryChannel {
        override fun sendCode(phone: String, code: String) = send(phone, code)
    }

    private val ordered: List<OtpDeliveryChannel> =
        resolveOrder(whatsAppService, smsService, channels, properties.deliveryOrder)

    /** Names of the channels that will be tried, in order. Never empty. */
    val activeChannels: List<String> = ordered.map { key(it.name) }

    private fun resolveOrder(
        whatsAppService: Optional<WhatsAppService>,
        smsService: SmsService,
        channels: List<OtpDeliveryChannel>,
        deliveryOrder: List<String>
    ): List<OtpDeliveryChannel> {
        val duplicated = channels.groupBy { key(it.name) }.filterValues { it.size > 1 }.keys
        check(duplicated.isEmpty()) {
            "more than one OtpDeliveryChannel bean is named $duplicated — delivery-order cannot tell them apart"
        }

        val available = linkedMapOf<String, OtpDeliveryChannel>()
        whatsAppService.ifPresent { wa -> available[WHATSAPP] = Adapter(WHATSAPP, wa::sendCode) }
        available[SMS] = Adapter(SMS, smsService::sendCode)
        channels.forEach { available[key(it.name)] = it }

        val order = deliveryOrder.map(::key).filter { it.isNotEmpty() }.distinct()
        val resolved = order.mapNotNull { available[it] }

        check(resolved.isNotEmpty()) {
            "app.auth.otp.delivery-order=$order names no available channel — available: ${available.keys}. " +
                "A channel is available when its bean exists (e.g. app.auth.telegram-gateway.enabled=true " +
                "for telegram, app.twilio.whatsapp.enabled=true for whatsapp)"
        }
        val skipped = order.filterNot { it in available }
        if (skipped.isNotEmpty()) {
            logger.info(
                "OTP delivery: channels {} are not configured and will be skipped; delivery order {}",
                skipped, order.filter { it in available }
            )
        }
        return resolved
    }

    fun sendCode(phoneE164: String, code: String) {
        val failures = mutableListOf<String>()
        ordered.forEachIndexed { index, channel ->
            try {
                channel.sendCode(phoneE164, code)
                return
            } catch (ex: RuntimeException) {
                // Only the exception class: a provider's message echoes its request (Twilio's
                // ApiException names the "To" number) and may carry the code, in formats no mask
                // would reliably catch. The phone is logged masked.
                failures += "${activeChannels[index]} (${ex.javaClass.name})"
                if (index < ordered.lastIndex) {
                    logger.warn(
                        "OTP delivery via {} failed for {}, falling back to {}: {}",
                        activeChannels[index],
                        maskPhone(phoneE164),
                        activeChannels[index + 1],
                        ex.javaClass.name
                    )
                }
            }
        }
        // Not the last channel's own exception: it would reach the exception handler's log, and
        // the client's response, with its text. See OtpDeliveryException.
        throw OtpDeliveryException(
            "OTP delivery failed on every channel for ${maskPhone(phoneE164)}: ${failures.joinToString(", ")}"
        )
    }
}
