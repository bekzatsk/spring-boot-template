package kz.innlab.starter.shared.error

/**
 * Thrown when a requested record does not exist. Mapped to HTTP 404.
 *
 * Not to be confused with Spring Security's AccessDeniedException, which was previously
 * reused for this case: it is not handled by the exception advice, so callers received a
 * 500 with "An unexpected error occurred" instead of a 404.
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)
