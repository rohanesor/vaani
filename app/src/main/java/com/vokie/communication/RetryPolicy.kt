package com.vokie.communication

object RetryPolicy {
    const val MAX_RETRIES = 3
    fun delayMillis(retryCount: Int): Long = when (retryCount) { 1 -> 1_000L; 2 -> 3_000L; else -> 8_000L }
    fun exhausted(retryCount: Int) = retryCount >= MAX_RETRIES
}
