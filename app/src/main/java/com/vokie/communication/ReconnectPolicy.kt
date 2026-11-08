package com.vokie.communication

import kotlin.math.min
import kotlin.random.Random

/**
 * Bounded exponential backoff with jitter for transport reconnection.
 *
 * Formula: delay = min(baseMs * 2^attempt, maxDelayMs) + random(0..jitterMs)
 *
 * Research basis: standard exponential backoff (AWS Architecture Blog, Google Cloud
 * documentation, RFC 9171 DTN custody transfer). Jitter prevents thundering-herd
 * when multiple peers reconnect simultaneously after infrastructure recovery.
 */
class ReconnectPolicy(
    val maxAttempts: Int = 10,
    private val baseMs: Long = 2_000,
    private val maxDelayMs: Long = 30_000,
    private val jitterMs: Long = 1_000,
    private val random: Random = Random.Default,
) {
    fun delayMs(attempt: Int): Long {
        require(attempt >= 0)
        val exponential = min(baseMs * (1L shl attempt.coerceAtMost(15)), maxDelayMs)
        val jitter = if (jitterMs > 0) random.nextLong(jitterMs) else 0
        return exponential + jitter
    }

    fun exhausted(attempt: Int): Boolean = attempt >= maxAttempts

    companion object {
        /** Default policy for Wi-Fi Direct reconnection. */
        val WIFI_DIRECT = ReconnectPolicy(maxAttempts = 10, baseMs = 2_000, maxDelayMs = 30_000, jitterMs = 1_000)

        /**
         * Bluetooth RFCOMM reconnection.
         *
         * Bluetooth Classic does not require group formation or peer discovery before
         * reconnecting — `createRfcommSocketToServiceRecord` targets the peer directly.
         * Shorter base delay (1s) because the connect attempt itself is fast (~15s timeout).
         * More attempts (12) with a lower cap (20s) to cover ~3–4 minutes of recovery
         * window, appropriate for rescue/disaster intermittent radio conditions.
         */
        val BLUETOOTH = ReconnectPolicy(maxAttempts = 12, baseMs = 1_000, maxDelayMs = 20_000, jitterMs = 800)
    }
}
