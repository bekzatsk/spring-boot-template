package kz.innlab.starter.shared.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The limiter guards the login and change-password endpoints, so its edge cases are security
 * behaviour, not implementation detail: counting off by one lets an extra guess through, and a
 * key that never expires locks a user out for good.
 */
class InMemoryRateLimiterTest {

    private val limiter = InMemoryRateLimiter()

    @Test
    fun `allows exactly the configured number of attempts`() {
        repeat(3) { attempt ->
            assertThat(limiter.tryAcquire("k", limit = 3, windowSeconds = 60))
                .describedAs("attempt %d of 3", attempt + 1)
                .isTrue()
        }

        assertThat(limiter.tryAcquire("k", limit = 3, windowSeconds = 60)).isFalse()
    }

    @Test
    fun `keeps refusing once the limit is passed`() {
        repeat(4) { limiter.tryAcquire("k", limit = 2, windowSeconds = 60) }

        assertThat(limiter.tryAcquire("k", limit = 2, windowSeconds = 60)).isFalse()
    }

    @Test
    fun `counts each key separately`() {
        repeat(2) { limiter.tryAcquire("first", limit = 2, windowSeconds = 60) }

        assertThat(limiter.tryAcquire("first", limit = 2, windowSeconds = 60)).isFalse()
        assertThat(limiter.tryAcquire("second", limit = 2, windowSeconds = 60)).isTrue()
    }

    @Test
    fun `reset clears the counter`() {
        repeat(3) { limiter.tryAcquire("k", limit = 2, windowSeconds = 60) }
        assertThat(limiter.tryAcquire("k", limit = 2, windowSeconds = 60)).isFalse()

        limiter.reset("k")

        assertThat(limiter.tryAcquire("k", limit = 2, windowSeconds = 60)).isTrue()
    }

    @Test
    fun `a window that has elapsed starts over`() {
        // Zero-length window: every call is a fresh window, so the limit can never bite.
        repeat(5) {
            assertThat(limiter.tryAcquire("k", limit = 1, windowSeconds = 0)).isTrue()
        }
    }

    @Test
    fun `fails open rather than refusing everyone once at capacity`() {
        val tiny = InMemoryRateLimiter(maxEntries = 2)
        // Long window so nothing can be purged as expired.
        tiny.tryAcquire("a", limit = 1, windowSeconds = 3600)
        tiny.tryAcquire("b", limit = 1, windowSeconds = 3600)

        // Third key cannot be tracked; refusing it would turn the limiter into a DoS lever.
        assertThat(tiny.tryAcquire("c", limit = 1, windowSeconds = 3600)).isTrue()
        assertThat(tiny.tryAcquire("c", limit = 1, windowSeconds = 3600)).isTrue()
    }

    @Test
    fun `concurrent attempts on one key do not exceed the limit`() {
        val threads = 16
        val limit = 5
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val allowed = AtomicInteger()

        repeat(threads) {
            pool.submit {
                start.await()
                if (limiter.tryAcquire("shared", limit, windowSeconds = 60)) allowed.incrementAndGet()
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        assertThat(allowed.get())
            .describedAs("a racing caller must not slip past the limit")
            .isEqualTo(limit)
    }
}
