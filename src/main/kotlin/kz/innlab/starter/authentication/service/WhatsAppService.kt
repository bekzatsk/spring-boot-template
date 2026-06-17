package kz.innlab.starter.authentication.service

/**
 * Low-level WhatsApp delivery abstraction.
 * Implementations send a one-time code to a phone number via WhatsApp.
 * Implementations MUST throw on any delivery failure so the orchestrator
 * (OtpDeliveryService) can fall back to SMS.
 */
interface WhatsAppService {
    fun sendCode(phone: String, code: String)
}
