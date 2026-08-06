package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.OtpSendResult
import kz.innlab.starter.authentication.model.SmsVerification
import kz.innlab.starter.authentication.repository.SmsVerificationRepository
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SmsVerificationService(
    private val smsVerificationRepository: SmsVerificationRepository,
    private val otpDeliveryService: OtpDeliveryService,
    private val passwordEncoder: PasswordEncoder,
    private val afterCommitRunner: AfterCommitRunner,
    @Value("\${app.auth.sms.dev-code:}") private val devCode: String = ""
) {

    companion object {
        private const val EXPIRY_MINUTES = 5L
        private const val RATE_LIMIT_SECONDS = 60L
        private const val MAX_ATTEMPTS = 3
    }

    @Transactional
    fun sendCode(phoneE164: String): OtpSendResult {
        val now = Instant.now()
        // Rate limit: max 1 OTP request per phone per 60 seconds
        if (smsVerificationRepository.existsByPhoneAndCreatedAtAfter(phoneE164, now.minusSeconds(RATE_LIMIT_SECONDS))) {
            throw IllegalStateException("Please wait before requesting a new code")
        }
        val code = OneTimeCodes.generate(devCode)
        val hash = requireNotNull(passwordEncoder.encode(code)) { "PasswordEncoder returned null hash" }
        // Delete existing codes for this phone before issuing new one
        smsVerificationRepository.deleteAllByPhone(phoneE164)
        val saved = smsVerificationRepository.save(
            SmsVerification(
                phone = phoneE164,
                codeHash = hash,
                expiresAt = now.plusSeconds(EXPIRY_MINUTES * 60)
            )
        )
        // Deferred to after commit: the SMS provider HTTP call must not hold the DB transaction open.
        afterCommitRunner.run { otpDeliveryService.sendCode(phoneE164, code) }
        return OtpSendResult(
            verificationId = saved.id,
            resendAvailableAt = now.plusSeconds(RATE_LIMIT_SECONDS),
            retryAfterSeconds = RATE_LIMIT_SECONDS
        )
    }

    @Transactional
    fun verifyCode(verificationId: UUID, phoneE164: String, code: String): Boolean {
        val record = smsVerificationRepository.findById(verificationId).orElse(null) ?: return false
        // Phone mismatch: reject to prevent UUID guessing across phone numbers
        if (record.phone != phoneE164) return false
        if (record.used) return false
        if (record.expiresAt <= Instant.now()) return false
        if (record.attempts >= MAX_ATTEMPTS) return false
        // Increment attempts before checking — prevents brute force by counting failed attempts
        record.attempts++
        smsVerificationRepository.save(record)
        if (!passwordEncoder.matches(code, record.codeHash)) return false
        record.used = true
        smsVerificationRepository.save(record)
        return true
    }
}
