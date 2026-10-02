package kz.innlab.starter.shared.ratelimit

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fixed-window counters held in a ConcurrentHashMap.
 *
 * Keys are attacker-supplied (an email, an IP), so the map is capacity-bounded: without a cap,
 * hammering the endpoint with fresh keys would be a memory-exhaustion vector.
 *
 * At capacity the limiter makes room instead of refusing new keys. Refusing them let anyone who
 * filled the map with fresh keys lock every other user out (429 for all newcomers). Expired
 * entries go first; if that is not enough, the entries with the lowest counts are evicted in a
 * batch. A flood consists of fresh keys at count 1, so it mostly evicts itself, while a key that
 * has piled up failures — the address under attack — outlives it. Evicting a batch at a time
 * keeps the full scan rare: one pass per [EVICTION_FRACTION] of capacity.
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
        if (windows[key] == null && windows.size >= maxEntries) {
            makeRoom(now)
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

    private fun makeRoom(now: Instant) {
        windows.entries.removeIf { it.value.expiresAt <= now }
        val target = maxEntries - maxOf(1, (maxEntries * EVICTION_FRACTION).toInt())
        if (windows.size <= target) return

        // Lowest counts first: raise the threshold until enough entries are gone.
        var threshold = windows.values.minOf { it.count.get() }
        while (windows.size > target) {
            val ceiling = threshold
            windows.entries.removeIf { windows.size > target && it.value.count.get() <= ceiling }
            threshold++
        }
    }

    companion object {
        /** Share of capacity freed per eviction pass. */
        private const val EVICTION_FRACTION = 0.1
    }
}
