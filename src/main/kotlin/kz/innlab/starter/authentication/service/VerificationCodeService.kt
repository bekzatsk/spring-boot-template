package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.model.VerificationCode
import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class VerificationCodeService(
    private val verificationCodeRepository: VerificationCodeRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.auth.verification.dev-code:}") private val devCode: String = ""
) {

    companion object {
        private const val CODE_BOUND = 1_000_000
        private const val EXPIRY_MINUTES = 15L
        private const val RATE_LIMIT_SECONDS = 60L
        private const val MAX_ATTEMPTS = 3
        private val random = SecureRandom()
    }

    @Transactional
    fun createCode(
        identifier: String,
        purpose: VerificationPurpose,
        newValue: String? = null,
        userId: UUID? = null
    ): Pair<UUID, String> {
        // Rate limit: max 1 request per identifier+purpose per 60 seconds
        if (verificationCodeRepository.existsByIdentifierAndPurposeAndCreatedAtAfter(
                identifier, purpose, Instant.now().minusSeconds(RATE_LIMIT_SECONDS)
            )
        ) {
            throw IllegalStateException("Please wait before requesting a new code")
        }

        val code = if (devCode.isNotBlank()) devCode else String.format("%06d", random.nextInt(CODE_BOUND))
        val hash = passwordEncoder.encode(code)!!

        // Delete existing codes for same identifier+purpose before issuing new one
        verificationCodeRepository.deleteAllByIdentifierAndPurpose(identifier, purpose)

        val saved = verificationCodeRepository.save(
            VerificationCode(
                identifier = identifier,
                purpose = purpose,
                codeHash = hash,
                expiresAt = Instant.now().plusSeconds(EXPIRY_MINUTES * 60),
                newValue = newValue,
                userId = userId
            )
        )

        return Pair(saved.id, code)
    }

    @Transactional
    fun verifyCode(
        verificationId: UUID,
        identifier: String,
        purpose: VerificationPurpose,
        code: String
    ): VerificationCode {
        val record = verificationCodeRepository.findById(verificationId).orElseThrow {
            BadCredentialsException("Invalid verification code")
        }

        if (record.identifier != identifier) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.purpose != purpose) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.used) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.expiresAt <= Instant.now()) {
            throw BadCredentialsException("Invalid verification code")
        }
        if (record.attempts >= MAX_ATTEMPTS) {
            throw BadCredentialsException("Invalid verification code")
        }

        // Increment attempts before checking code — prevents brute force
        record.attempts++
        verificationCodeRepository.save(record)

        if (!passwordEncoder.matches(code, record.codeHash)) {
            throw BadCredentialsException("Invalid verification code")
        }

        record.used = true
        verificationCodeRepository.save(record)

        return record
    }
}
