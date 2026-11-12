package kz.innlab.starter.authentication.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The Telegram Gateway `RequestStatus` object, as POSTed to `callback-url` in a delivery report.
 * Only the fields the starter reads are declared. `verification_status.code_entered` is left out
 * on purpose: it is a code a user typed and must never reach a log.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramGatewayRequestStatus(
    @param:JsonProperty("request_id") val requestId: String? = null,
    @param:JsonProperty("phone_number") val phoneNumber: String? = null,
    @param:JsonProperty("delivery_status") val deliveryStatus: TelegramGatewayStatus? = null,
    @param:JsonProperty("verification_status") val verificationStatus: TelegramGatewayStatus? = null
)

/** `DeliveryStatus` (sent | delivered | read | expired | revoked) or `VerificationStatus`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramGatewayStatus(
    val status: String? = null,
    @param:JsonProperty("updated_at") val updatedAt: Long? = null
)
