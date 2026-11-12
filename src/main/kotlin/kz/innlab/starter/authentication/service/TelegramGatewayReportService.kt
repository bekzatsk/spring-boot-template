package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.TelegramGatewayRequestStatus
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.config.TelegramGatewayProperties
import kz.innlab.starter.shared.ratelimit.RateLimitExceededException
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.shared.util.maskPhone
import kz.innlab.starter.shared.util.normalizeToE164
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Acts on Telegram Gateway delivery reports whose signature TelegramGatewayCallbackController has
 * already checked.
 *
 * Every report is logged with the phone masked; `code_entered` is never read. With
 * `app.auth.telegram-gateway.fallback-on-undelivered`, an `expired` delivery — Telegram gave up
 * after `ttl-seconds` — replaces the verification's code ([VerificationCodeService.reissueCode]:
 * same verification id, expiry and attempt count, old code dead) and sends the new one, after
 * commit, through the channels after `telegram` in `app.auth.otp.delivery-order`.
 *
 * A report for an unknown request id, a verification that is used, expired or already reissued, or
 * a spent send limit changes nothing. Never throws for those: the controller answers 200 either way.
 */
@Service
@ConditionalOnProperty(name = ["app.auth.telegram-gateway.enabled"], havingValue = "true")
class TelegramGatewayReportService(
    private val properties: TelegramGatewayProperties,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val verificationCodeService: VerificationCodeService,
    private val otpDeliveryService: OtpDeliveryService,
    private val afterCommitRunner: AfterCommitRunner
) {

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramGatewayReportService::class.java)

        const val DELIVERY_EXPIRED = "expired"

        /** Purposes whose record holds the number the code went to. */
        private val PHONE_PURPOSES = setOf(VerificationPurpose.PHONE_LOGIN, VerificationPurpose.CHANGE_PHONE)
    }

    @Transactional
    fun handle(report: TelegramGatewayRequestStatus) {
        val requestId = report.requestId
        val delivery = report.deliveryStatus?.status
        // The Gateway writes E.164 without the '+'.
        val phone = report.phoneNumber?.trim()?.let { raw ->
            runCatching { normalizeToE164(if (raw.startsWith("+")) raw else "+$raw") }.getOrNull()
        }
        logger.info(
            "[TELEGRAM-GATEWAY] report request_id={} phone={} delivery={} verification={}",
            requestId, phone?.let(::maskPhone) ?: "?", delivery ?: "-", report.verificationStatus?.status ?: "-"
        )

        if (delivery != DELIVERY_EXPIRED || !properties.fallbackOnUndelivered) return
        if (requestId.isNullOrBlank() || phone == null) return

        val record = verificationCodeRepository.findByGatewayRequestId(requestId)
        if (record == null) {
            logger.debug("[TELEGRAM-GATEWAY] request_id={} matches no live verification", requestId)
            return
        }
        val recordPhone = when (record.purpose) {
            VerificationPurpose.PHONE_LOGIN -> record.identifier
            VerificationPurpose.CHANGE_PHONE -> record.newValue
            else -> null
        }
        if (record.purpose in PHONE_PURPOSES && recordPhone != phone) {
            logger.warn("[TELEGRAM-GATEWAY] request_id={} reports a different number than it was sent to", requestId)
            return
        }

        val fallback = otpDeliveryService.channelsAfter(TelegramGatewayService.CHANNEL_NAME)
        if (fallback.isEmpty()) {
            logger.warn(
                "[TELEGRAM-GATEWAY] code for {} was not delivered and no channel follows telegram in " +
                    "app.auth.otp.delivery-order — nothing to fall back to",
                maskPhone(phone)
            )
            return
        }

        val issued = try {
            verificationCodeService.reissueCode(record.id, requestId)
        } catch (ex: RateLimitExceededException) {
            logger.warn("[TELEGRAM-GATEWAY] code for {} was not delivered; send limit reached, no fallback", maskPhone(phone))
            return
        } ?: return

        logger.info(
            "[TELEGRAM-GATEWAY] code for {} was not delivered; new code goes through {}",
            maskPhone(phone), fallback
        )
        afterCommitRunner.run {
            try {
                otpDeliveryService.sendCodeAfter(TelegramGatewayService.CHANNEL_NAME, phone, issued.code, issued.verificationId)
            } catch (ex: RuntimeException) {
                // OtpDeliveryException carries masked phone and class names only.
                logger.warn("[TELEGRAM-GATEWAY] fallback delivery failed: {}", ex.message)
            }
        }
    }
}
