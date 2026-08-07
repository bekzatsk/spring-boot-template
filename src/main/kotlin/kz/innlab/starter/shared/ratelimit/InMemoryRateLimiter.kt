package kz.innlab.starter.shared.ratelimit

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fixed-window counters held in a ConcurrentHashMap.
 *
 * Keys are attacker-supplied (an email, an IP), so the map is capacity-bounded: without a cap,
 * hammering the endpoint with fresh keys would be a memory-exhaustion vector. When the cap is
 * reached expired entries are dropped, and if that does not free space the limiter fails open —
 * degrading availability for everyone is worse than letting the limit slip for a moment.
 */
class InMemoryRateLimiter(
    private val maxEntries: Int = 100_000
) : RateLimiter {

    private data class Window(val startedAt: Instant, val count: AtomicInteger)

    private val windows = ConcurrentHashMap<String, Window>()

    companion object {
        private val logger = LoggerFactory.getLogger(InMemoryRateLimiter::class.java)
    }

    override fun tryAcquire(key: String, limit: Int, windowSeconds: Long): Boolean {
        val now = Instant.now()

        if (windows.size >= maxEntries) {
            purgeExpired(now, windowSeconds)
            if (windows.size >= maxEntries) {
                logger.warn("Rate limiter at capacity ({} keys); allowing request", maxEntries)
                return true
            }
        }

        val window = windows.compute(key) { _, existing ->
            if (existing == null || existing.startedAt.plusSeconds(windowSeconds) <= now) {
                Window(now, AtomicInteger(0))
            } else {
                existing
            }
        } ?: return true

        return window.count.incrementAndGet() <= limit
    }

    override fun reset(key: String) {
        windows.remove(key)
    }

    private fun purgeExpired(now: Instant, windowSeconds: Long) {
        windows.entries.removeIf { it.value.startedAt.plusSeconds(windowSeconds) <= now }
    }
}
