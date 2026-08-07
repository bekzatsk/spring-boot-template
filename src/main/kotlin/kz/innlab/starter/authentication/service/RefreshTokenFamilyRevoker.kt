package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.repository.RefreshTokenRepository
import kz.innlab.starter.user.model.User
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Deletes a user's refresh tokens in its own transaction.
 *
 * Reuse detection ends by throwing, which rolls the caller's transaction back — and the
 * revocation with it. The family therefore survived exactly the event it exists to punish:
 * a replayed token left every session intact.
 *
 * Separate bean because a REQUIRES_NEW call from inside the same class bypasses the proxy.
 */
@Component
class RefreshTokenFamilyRevoker(
    private val refreshTokenRepository: RefreshTokenRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeAllFor(user: User) {
        refreshTokenRepository.deleteAllByUser(user)
    }
}
