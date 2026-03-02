package kz.innlab.template.authentication.service

import kz.innlab.template.authentication.model.SmsVerification
import kz.innlab.template.authentication.repository.SmsVerificationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class SmsVerificationService(
    private val smsVerificationRepository: SmsVerificationRepository,
    private val smsService: SmsService,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.auth.sms.dev-code:}") private val devCode: String = ""
) {

    companion object {
        private const val CODE_BOUND = 1_000_000
        private const val EXPIRY_MINUTES = 5L
        private const val RATE_LIMIT_SECONDS = 60L
        private const val MAX_ATTEMPTS = 3
        private val random = SecureRandom()
    }

    @Transactional
    fun sendCode(phoneE164: String): UUID {
        // Rate limit: max 1 OTP request per phone per 60 seconds
        if (smsVerificationRepository.existsByPhoneAndCreatedAtAfter(phoneE164, Instant.now().minusSeconds(RATE_LIMIT_SECONDS))) {
            throw IllegalStateException("Please wait before requesting a new code")
        }
        val code = if (devCode.isNotBlank()) devCode else String.format("%06d", random.nextInt(CODE_BOUND))
        val hash = passwordEncoder.encode(code)!!
        // Delete existing codes for this phone before issuing new one
        smsVerificationRepository.deleteAllByPhone(phoneE164)
        val saved = smsVerificationRepository.save(
            SmsVerification(
                phone = phoneE164,
                codeHash = hash,
                expiresAt = Instant.now().plusSeconds(EXPIRY_MINUTES * 60)
            )
        )
        smsService.sendCode(phoneE164, code)
        return saved.id!!
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
