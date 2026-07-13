package kz.innlab.starter.authentication.dto

/**
 * Refresh/revoke request body. [refreshToken] is optional: when blank, the refresh token is read
 * from the refresh cookie (cookie mode). Priority is always body → cookie.
 */
data class RefreshRequest(
    val refreshToken: String = ""
)
