package kz.innlab.starter.shared.ratelimit

/** Thrown when a caller exceeds an attempt limit. Mapped to HTTP 429. */
class RateLimitExceededException(
    message: String,
    val retryAfterSeconds: Long
) : RuntimeException(message)
