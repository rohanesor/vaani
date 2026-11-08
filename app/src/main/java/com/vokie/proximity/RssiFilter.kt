package com.vokie.proximity

/** Deterministic rolling median; output is radio evidence only, never distance. */
class RssiFilter(
    private val windowSize: Int = 5,
    private val stableDeltaDb: Double = 2.0,
    private val unstableRangeDb: Int = 12,
    private val staleAfterMs: Long = 30_000L,
) {
    init { require(windowSize > 0 && stableDeltaDb >= 0 && unstableRangeDb >= 0 && staleAfterMs > 0) }
    private val values = ArrayDeque<ProximityTelemetry>()
    private var previous: Double? = null

    fun add(sample: ProximityTelemetry): FilteredRssi {
        values.addLast(sample); if (values.size > windowSize) values.removeFirst()
        val ordered = values.map { it.rssiDbm }.sorted(); val median = ordered[ordered.size / 2].toDouble()
        val trend = when {
            previous == null -> RssiTrend.UNKNOWN
            ordered.last() - ordered.first() >= unstableRangeDb -> RssiTrend.UNSTABLE
            median - requireNotNull(previous) > stableDeltaDb -> RssiTrend.STRENGTHENING
            median - requireNotNull(previous) < -stableDeltaDb -> RssiTrend.WEAKENING
            else -> RssiTrend.STABLE
        }
        previous = median
        return FilteredRssi(median, trend, values.size, RssiFreshness.CURRENT)
    }

    fun current(now: Long): FilteredRssi {
        val last = values.lastOrNull() ?: return FilteredRssi(null, RssiTrend.UNKNOWN, 0, RssiFreshness.UNAVAILABLE)
        if (now < last.timestamp || now - last.timestamp > staleAfterMs) return FilteredRssi(null, RssiTrend.UNKNOWN, values.size, RssiFreshness.STALE)
        val ordered = values.map { it.rssiDbm }.sorted()
        return FilteredRssi(ordered[ordered.size / 2].toDouble(), RssiTrend.UNKNOWN, values.size, RssiFreshness.CURRENT)
    }
}
