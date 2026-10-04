package kz.innlab.starter.shared.error

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Thrown when an authenticated caller attempts an operation on a resource they do not own
 * (e.g. sending a push notification to another user's device token). Mapped to HTTP 403.
 *
 * `@ResponseStatus` covers consumer controllers too: the starter's exception handler is scoped to
 * its own package, and a consumer endpoint that calls a starter service would otherwise turn
 * this into a 500.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
open class ForbiddenOperationException(message: String) : RuntimeException(message)

/** The caller's login is too old for the operation; the client should send them through login again. */
@ResponseStatus(HttpStatus.FORBIDDEN, reason = "Log in again to make this change")
class RecentLoginRequiredException(message: String = "Log in again to make this change") :
    ForbiddenOperationException(message)
