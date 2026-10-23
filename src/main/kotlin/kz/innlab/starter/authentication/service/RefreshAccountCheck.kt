package kz.innlab.starter.authentication.service

import kz.innlab.starter.user.model.User

/**
 * Asked before a refresh token is rotated whether its owner may keep their session — for accounts
 * that live in an external directory, where they can be disabled without the application knowing.
 *
 * Runs outside any transaction, before the rotation, so a directory outage cannot cost the client
 * its refresh token. Every bean of this type is asked, in order.
 */
fun interface RefreshAccountCheck {

    /**
     * Returns normally to allow the refresh. Throws BadCredentialsException to end every session
     * of the user, or [DirectoryUnavailableException] to refuse this refresh only (503).
     */
    fun check(user: User)
}
