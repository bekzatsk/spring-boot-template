package kz.innlab.starter.shared.ratelimit

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fixed-window counters held in a ConcurrentHashMap.
 *
 * Keys are attacker-supplied (an email, an IP), so the map is capacity-bounded: without a cap,
 * hammering the endpoint with fresh keys would be a memory-exhaustion vector. When the cap is
 * reached expired entries are dropped. If no space remains, a new key is rejected while
 * existing keys keep their counters. An attacker cannot turn capacity exhaustion into unlimited
 * credential guesses.
 */
class InMemoryRateLimiter(
    private val maxEntries: Int = 100_000
) : RateLimiter {

    private data class Window(val expiresAt: Instant, val count: AtomicInteger)

    private val windows = ConcurrentHashMap<String, Window>()

    init { require(maxEntries > 0) { "maxEntries must be positive" } }

    @Synchronized
    override fun tryAcquire(key: String, limit: Int, windowSeconds: Long): Boolean {
        val now = Instant.now()
        val existing = windows[key]
        if (existing == null && windows.size >= maxEntries) {
            purgeExpired(now)
            if (windows.size >= maxEntries) return false
        }

        val window = windows.compute(key) { _, existing ->
            if (existing == null || existing.expiresAt <= now) {
                Window(now.plusSeconds(windowSeconds), AtomicInteger(0))
            } else {
                existing
            }
        } ?: return false

        return window.count.incrementAndGet() <= limit
    }

    @Synchronized
    override fun reset(key: String) {
        windows.remove(key)
    }

    private fun purgeExpired(now: Instant) {
        windows.entries.removeIf { it.value.expiresAt <= now }
    }
}
