package kz.innlab.starter.shared.ratelimit

/**
 * Counts attempts against a key within a rolling window.
 *
 * The default implementation keeps counters in memory, so limits apply per application
 * instance: behind N instances an attacker gets N times the attempts. Declare a bean of this
 * type backed by Redis (or any shared store) to make the limit cluster-wide.
 */
interface RateLimiter {

    /**
     * Records an attempt for [key] and reports whether it is allowed.
     * Returns false once [limit] attempts have been made inside [windowSeconds].
     */
    fun tryAcquire(key: String, limit: Int, windowSeconds: Long): Boolean

    /** Clears the counter for [key] — call after a success so a legitimate user is not punished. */
    fun reset(key: String)
}
