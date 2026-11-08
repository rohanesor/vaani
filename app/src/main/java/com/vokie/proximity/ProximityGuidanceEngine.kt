package com.vokie.proximity

import com.vokie.location.LocationConfidence
import com.vokie.location.LocationFreshness
import com.vokie.location.LocationGuidance

enum class ProximityGuidanceState { SEARCHING, LOCATION_RECEIVED, GUIDANCE_ACTIVE, GETTING_CLOSER, GETTING_FARTHER, STABLE, SIGNAL_UNRELIABLE, LOCATION_STALE, NEARBY }
data class ProximityGuidance(val state: ProximityGuidanceState, val location: LocationGuidance, val signalTrend: RssiTrend, val confidence: LocationConfidence)

/** GPS is authoritative for direction. RSSI only confirms/conflicts with movement evidence. */
class ProximityGuidanceEngine(private val nearbyMeters: Double = 20.0, private val distanceDeltaMeters: Double = 3.0) {
    fun guide(location: LocationGuidance, previousDistanceMeters: Double?, signal: FilteredRssi?): ProximityGuidance {
        if (location.freshness == LocationFreshness.STALE) return ProximityGuidance(ProximityGuidanceState.LOCATION_STALE, location, signal?.trend ?: RssiTrend.UNKNOWN, LocationConfidence.STALE)
        if (location.freshness == LocationFreshness.UNAVAILABLE) return ProximityGuidance(ProximityGuidanceState.SEARCHING, location, signal?.trend ?: RssiTrend.UNKNOWN, LocationConfidence.INVALID)
        if (location.distanceMeters != null && location.distanceMeters <= nearbyMeters) return ProximityGuidance(ProximityGuidanceState.NEARBY, location, signal?.trend ?: RssiTrend.UNKNOWN, location.confidence)
        val trend = signal?.takeIf { it.freshness == RssiFreshness.CURRENT }?.trend ?: RssiTrend.UNKNOWN
        if (trend == RssiTrend.UNSTABLE) return ProximityGuidance(ProximityGuidanceState.SIGNAL_UNRELIABLE, location, trend, LocationConfidence.LOW)
        val gpsMovement = previousDistanceMeters?.let { previous ->
            when {
                location.distanceMeters!! < previous - distanceDeltaMeters -> RssiTrend.STRENGTHENING
                location.distanceMeters > previous + distanceDeltaMeters -> RssiTrend.WEAKENING
                else -> RssiTrend.STABLE
            }
        } ?: RssiTrend.UNKNOWN
        val contradictory = (gpsMovement == RssiTrend.STRENGTHENING && trend == RssiTrend.WEAKENING) || (gpsMovement == RssiTrend.WEAKENING && trend == RssiTrend.STRENGTHENING)
        val state = when {
            contradictory -> ProximityGuidanceState.SIGNAL_UNRELIABLE
            gpsMovement == RssiTrend.STRENGTHENING && trend in setOf(RssiTrend.STRENGTHENING, RssiTrend.UNKNOWN) -> ProximityGuidanceState.GETTING_CLOSER
            gpsMovement == RssiTrend.WEAKENING && trend in setOf(RssiTrend.WEAKENING, RssiTrend.UNKNOWN) -> ProximityGuidanceState.GETTING_FARTHER
            gpsMovement == RssiTrend.STABLE -> ProximityGuidanceState.STABLE
            else -> ProximityGuidanceState.GUIDANCE_ACTIVE
        }
        return ProximityGuidance(state, location, trend, if (contradictory) LocationConfidence.LOW else location.confidence)
    }
}
