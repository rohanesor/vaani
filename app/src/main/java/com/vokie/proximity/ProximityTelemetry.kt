package com.vokie.proximity

import com.vokie.domain.model.TransportType

data class ProximityTelemetry(val sourceDeviceId: String, val transportType: TransportType, val rssiDbm: Int, val timestamp: Long) {
    init { require(sourceDeviceId.isNotBlank() && timestamp > 0) }
}
enum class RssiTrend { STRENGTHENING, WEAKENING, STABLE, UNSTABLE, UNKNOWN }
data class FilteredRssi(val rssiDbm: Double?, val trend: RssiTrend, val sampleCount: Int, val freshness: RssiFreshness)
enum class RssiFreshness { CURRENT, STALE, UNAVAILABLE }
