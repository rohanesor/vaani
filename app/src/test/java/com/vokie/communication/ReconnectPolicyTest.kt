package com.vokie.communication

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ReconnectPolicyTest {
    // TEST 1: exponential backoff calculation
    @Test fun `delay grows exponentially from base`() {
        val policy = ReconnectPolicy(maxAttempts = 10, baseMs = 2_000, maxDelayMs = 30_000, jitterMs = 0, random = Random(42))
        assertEquals(2_000, policy.delayMs(0))   // 2000 * 2^0
        assertEquals(4_000, policy.delayMs(1))   // 2000 * 2^1
        assertEquals(8_000, policy.delayMs(2))   // 2000 * 2^2
        assertEquals(16_000, policy.delayMs(3))  // 2000 * 2^3
        assertEquals(30_000, policy.delayMs(4))  // capped at 30_000
        assertEquals(30_000, policy.delayMs(5))  // stays capped
    }

    // TEST 2: maximum delay is bounded
    @Test fun `delay never exceeds maxDelayMs plus jitter`() {
        val policy = ReconnectPolicy(maxAttempts = 20, baseMs = 1_000, maxDelayMs = 10_000, jitterMs = 500)
        repeat(20) { attempt ->
            val delay = policy.delayMs(attempt)
            assertTrue("attempt $attempt delay=$delay exceeded bound", delay <= 10_000 + 500)
        }
    }

    // TEST 3: jitter stays within configured bounds
    @Test fun `jitter is bounded`() {
        val policy = ReconnectPolicy(maxAttempts = 100, baseMs = 1_000, maxDelayMs = 1_000, jitterMs = 500)
        val delays = (0 until 100).map { policy.delayMs(0) }
        // All delays should be in [1000, 1500)
        assertTrue(delays.all { it in 1_000 until 1_500 })
        // With 100 samples, jitter should produce variation
        assertTrue("jitter should produce variation", delays.toSet().size > 1)
    }

    // TEST 4: zero jitter produces deterministic output
    @Test fun `zero jitter is deterministic`() {
        val policy = ReconnectPolicy(baseMs = 1_000, maxDelayMs = 10_000, jitterMs = 0)
        assertEquals(policy.delayMs(0), policy.delayMs(0))
        assertEquals(policy.delayMs(3), policy.delayMs(3))
    }

    // TEST 5: retry exhaustion
    @Test fun `exhaustion boundary is exact`() {
        val policy = ReconnectPolicy(maxAttempts = 5)
        assertFalse(policy.exhausted(0))
        assertFalse(policy.exhausted(4))
        assertTrue(policy.exhausted(5))
        assertTrue(policy.exhausted(100))
    }

    // TEST 6: default Wi-Fi Direct policy has sensible values
    @Test fun `wifi direct default policy is sensible`() {
        val p = ReconnectPolicy.WIFI_DIRECT
        assertEquals(10, p.maxAttempts)
        // First delay should be around 2000ms + jitter
        val d0 = p.delayMs(0)
        assertTrue("first delay $d0 should be >= 2000", d0 >= 2_000)
        assertTrue("first delay $d0 should be < 3100", d0 < 3_100)
    }

    // TEST 7: very large attempt number does not overflow
    @Test fun `large attempt does not overflow`() {
        val policy = ReconnectPolicy(maxAttempts = 1000, baseMs = 1_000, maxDelayMs = 60_000, jitterMs = 0)
        val delay = policy.delayMs(100) // 2^100 would overflow, but coerced to 15
        assertTrue(delay in 1_000..60_000)
    }
}
