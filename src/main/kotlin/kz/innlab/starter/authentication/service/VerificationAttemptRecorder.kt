package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Records a verification attempt in its own transaction.
 *
 * A failed verification throws, which rolls the caller's transaction back — and with it the
 * attempt counter, so the brute-force limit never actually bit. REQUIRES_NEW commits the
 * increment independently of that rollback.
 *
 * Separate bean on purpose: calling a REQUIRES_NEW method from inside the same class would
 * bypass the proxy and silently do nothing.
 */
@Component
class VerificationAttemptRecorder(
    private val verificationCodeRepository: VerificationCodeRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordAttempt(verificationId: UUID, maxAttempts: Int): Boolean =
        verificationCodeRepository.incrementAttemptsIfActive(
            verificationId, Instant.now(), maxAttempts
        ) == 1

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markUsed(verificationId: UUID, maxAttempts: Int): Boolean =
        verificationCodeRepository.markUsedIfActive(
            verificationId, Instant.now(), maxAttempts
        ) == 1
}
