package kz.innlab.starter.user.service

import kz.innlab.starter.user.model.User

/**
 * Port for revoking all of a user's refresh tokens on security-sensitive admin actions
 * (password reset, account deletion). Implemented by the authentication module
 * ([kz.innlab.starter.authentication.service.RefreshTokenService]) so the user module
 * never depends on authentication directly — that import direction created a module cycle.
 */
fun interface RefreshTokenRevoker {
    fun revokeAllFor(user: User)
}
