package com.vokie.location

import kotlin.math.*

enum class CardinalDirection { N, NE, E, SE, S, SW, W, NW }
enum class RelativeDirection { AHEAD, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, BEHIND, SHARP_LEFT, LEFT, SLIGHT_LEFT, UNKNOWN }
enum class LocationFreshness { CURRENT, STALE, UNAVAILABLE }
enum class LocationConfidence { HIGH, MEDIUM, LOW, STALE, INVALID }

data class LocationGuidance(
    val distanceMeters: Double?, val absoluteBearingDegrees: Double?, val relativeBearingDegrees: Double?,
    val cardinalDirection: CardinalDirection?, val relativeDirection: RelativeDirection,
    val confidence: LocationConfidence, val freshness: LocationFreshness,
)

/** Pure geographic guidance. Confidence is qualitative guidance evidence, never a probability. */
class LocationGuidanceEngine(
    private val staleAfterMs: Long = 60_000L,
    private val lowAccuracyMeters: Float = 50f,
    private val nearbyMeters: Double = 10.0,
) {
    init { require(staleAfterMs > 0 && lowAccuracyMeters >= 0 && nearbyMeters >= 0) }

    fun guide(sender: LocationMetadata, receiver: LocationMetadata, receiverHeadingDegrees: Double?, now: Long): LocationGuidance {
        if (sender.availability != LocationAvailability.AVAILABLE || receiver.availability != LocationAvailability.AVAILABLE) return unavailable()
        val timestamp = requireNotNull(sender.timestamp)
        if (now < timestamp || now - timestamp > staleAfterMs) return LocationGuidance(null, null, null, null, RelativeDirection.UNKNOWN, LocationConfidence.STALE, LocationFreshness.STALE)
        val distance = haversine(requireNotNull(receiver.latitude), requireNotNull(receiver.longitude), requireNotNull(sender.latitude), requireNotNull(sender.longitude))
        // Bearing is meaningless at coincident/nearly coincident fixes.
        if (distance <= nearbyMeters) return LocationGuidance(distance, null, null, null, RelativeDirection.UNKNOWN, confidence(sender, receiver, distance), LocationFreshness.CURRENT)
        val bearing = initialBearing(requireNotNull(receiver.latitude), requireNotNull(receiver.longitude), requireNotNull(sender.latitude), requireNotNull(sender.longitude))
        val relative = receiverHeadingDegrees?.takeIf(Double::isFinite)?.let { normalize(bearing - it) }
        return LocationGuidance(distance, bearing, relative, cardinal(bearing), relative?.let(::relativeDirection) ?: RelativeDirection.UNKNOWN, confidence(sender, receiver, distance), LocationFreshness.CURRENT)
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1); val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        return normalize(Math.toDegrees(atan2(sin(dLon) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon))))
    }
    fun normalize(degrees: Double): Double = ((degrees % 360) + 360) % 360
    fun cardinal(bearing: Double): CardinalDirection = CardinalDirection.entries[((normalize(bearing) + 22.5) / 45).toInt() % 8]
    fun relativeDirection(relativeBearing: Double): RelativeDirection = when (((normalize(relativeBearing) + 22.5) / 45).toInt() % 8) {
        0 -> RelativeDirection.AHEAD; 1 -> RelativeDirection.SLIGHT_RIGHT; 2 -> RelativeDirection.RIGHT; 3 -> RelativeDirection.SHARP_RIGHT
        4 -> RelativeDirection.BEHIND; 5 -> RelativeDirection.SHARP_LEFT; 6 -> RelativeDirection.LEFT; else -> RelativeDirection.SLIGHT_LEFT
    }

    private fun confidence(sender: LocationMetadata, receiver: LocationMetadata, distance: Double): LocationConfidence {
        val combined = requireNotNull(sender.accuracyMeters) + requireNotNull(receiver.accuracyMeters)
        return when {
            combined > lowAccuracyMeters * 2 || distance <= combined -> LocationConfidence.LOW
            combined > lowAccuracyMeters -> LocationConfidence.MEDIUM
            else -> LocationConfidence.HIGH
        }
    }
    private fun unavailable() = LocationGuidance(null, null, null, null, RelativeDirection.UNKNOWN, LocationConfidence.INVALID, LocationFreshness.UNAVAILABLE)
}
