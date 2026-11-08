package com.vokie.location

import com.vokie.proximity.FilteredRssi
import com.vokie.proximity.ProximityGuidance
import com.vokie.proximity.ProximityGuidanceEngine
import com.vokie.proximity.RssiTrend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EmergencyGuidanceState(
    val location: LocationGuidance,
    val proximity: ProximityGuidance?,
    val receiverAccuracyMeters: Float?,
    val senderAccuracyMeters: Float?,
    val combinedAccuracyMeters: Float?,
    val displayedDistanceMeters: Double?,
    val distanceTrend: DistanceTrend,
    val senderAgeMs: Long?,
    val filteredRssiDbm: Double?,
    val rssiTrend: RssiTrend,
    val timestamp: Long,
)

/** App-level telemetry combiner; sender metadata will remain local until a future wire-protocol milestone. */
class EmergencyGuidanceCoordinator(
    private val locationEngine: LocationGuidanceEngine = LocationGuidanceEngine(),
    private val proximityEngine: ProximityGuidanceEngine = ProximityGuidanceEngine(),
) {
    private val _state = MutableStateFlow(EmergencyGuidanceState(
        LocationGuidance(null, null, null, null, RelativeDirection.UNKNOWN, LocationConfidence.INVALID, LocationFreshness.UNAVAILABLE),
        null, null, null, null, null, DistanceTrend.UNRELIABLE, null, null, RssiTrend.UNKNOWN, 0,
    ))
    val state: StateFlow<EmergencyGuidanceState> = _state
    private val smoother = DistanceSmoother()
    private val trendClassifier = DistanceTrendClassifier()
    private var previousDistance: Double? = null
    fun update(sender: LocationMetadata, receiver: LocationMetadata, heading: Heading?, rssi: FilteredRssi?, now: Long): EmergencyGuidanceState {
        val guidance = locationEngine.guide(sender, receiver, heading?.degrees, now)
        if (guidance.freshness != LocationFreshness.CURRENT) {
            smoother.clear()
            trendClassifier.clear()
            previousDistance = null
        }
        val displayedDistance = smoother.add(guidance.distanceMeters)
        val distanceTrend = trendClassifier.add(guidance.distanceMeters, guidance.freshness)
        val proximity = proximityEngine.guide(guidance, previousDistance, rssi)
        previousDistance = displayedDistance
        val combinedAccuracy = if (sender.accuracyMeters != null && receiver.accuracyMeters != null) sender.accuracyMeters + receiver.accuracyMeters else null
        return EmergencyGuidanceState(guidance, proximity, receiver.accuracyMeters, sender.accuracyMeters, combinedAccuracy, displayedDistance, distanceTrend, sender.timestamp?.let { now - it }, rssi?.rssiDbm, rssi?.trend ?: RssiTrend.UNKNOWN, now).also { _state.value = it }
    }
}
