package kz.innlab.starter.shared.error

/**
 * Thrown when an authenticated caller attempts an operation on a resource they do not own
 * (e.g. sending a push notification to another user's device token). Mapped to HTTP 403.
 */
class ForbiddenOperationException(message: String) : RuntimeException(message)
