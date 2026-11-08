package com.vokie.communication

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AckAndRetryTest {
    @Test fun knownAckCompletesCorrectWaiterAndUnknownAckIsIgnored() = runBlocking {
        val tracker = AckTracker()
        val first = tracker.register("first")
        val second = tracker.register("second")
        assertFalse(tracker.acknowledge("unknown"))
        assertTrue(tracker.acknowledge("second"))
        assertTrue(second.await())
        assertFalse(first.isCompleted)
        tracker.remove("first")
    }

    @Test fun retryPolicyIsBoundedAndIncreasing() {
        assertFalse(RetryPolicy.exhausted(1)); assertFalse(RetryPolicy.exhausted(2)); assertTrue(RetryPolicy.exhausted(3))
        assertTrue(RetryPolicy.delayMillis(1) < RetryPolicy.delayMillis(2))
        assertTrue(RetryPolicy.delayMillis(2) < RetryPolicy.delayMillis(3))
    }
}
