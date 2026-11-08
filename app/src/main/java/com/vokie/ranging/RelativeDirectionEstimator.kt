package com.vokie.ranging

import kotlin.math.abs
import kotlin.math.sqrt

/** A direction result is deliberately nullable until an experiment validates a method. */
data class DirectionEstimate(
    val bearingDegrees: Double? = null,
    val directionSector: DirectionSector? = DirectionSector.UNKNOWN,
    val confidence: LocalizationConfidence = LocalizationConfidence.LOW,
    val method: DirectionMethod = DirectionMethod.NONE,
    val sampleCount: Int = 0,
    val timestamp: Long = 0L,
    val uncertaintyDegrees: Double? = null,
    val reason: String = "Insufficient validated directional evidence",
)

enum class DirectionMethod { NONE, RSSI_ANGULAR_MAXIMUM_HYPOTHESIS, RSSI_ANGULAR_GRADIENT_HYPOTHESIS, MOVEMENT_SIGNATURE_HYPOTHESIS }

data class DirectionObservation(
    val experimentId: String,
    val timestamp: Long,
    val peerId: String,
    val orientationDegrees: Double?,
    val rawRssi: Int?,
    val filteredRssi: Double?,
    val motionState: MotionState,
    val sensorAvailability: Set<String> = emptySet(),
)

data class AngularRssiProfile(
    val angleDegrees: Double,
    val rssiMedian: Double?,
    val rssiMean: Double?,
    val rssiVariance: Double?,
    val sampleCount: Int,
    val timestamps: List<Long> = emptyList(),
)

data class DirectionEvidence(
    val angularProfiles: List<AngularRssiProfile>,
    val orientationSpanDegrees: Double,
    val rssVariance: Double?,
    val stable: Boolean,
    val temporalGradient: Double?,
    val movementSamples: Int,
)

/**
 * Research evaluator for three hypotheses. It does not turn a compass heading into peer
 * direction, and currently emits UNKNOWN by default. A future validated policy may opt in.
 */
class RelativeDirectionEstimator(
    private val minimumSamples: Int = 20,
    private val minimumAngularCoverageDegrees: Double = 270.0,
    private val staleAfterMs: Long = 30_000L,
) {
    fun estimate(observations: List<DirectionObservation>, now: Long = System.currentTimeMillis()): DirectionEstimate {
        val current = observations.filter { now - it.timestamp in 0..staleAfterMs && it.orientationDegrees != null }
        if (current.size < minimumSamples) return unknown(observations.size, "Insufficient directional samples")
        val evidence = analyze(current)
        if (evidence.orientationSpanDegrees < minimumAngularCoverageDegrees) return unknown(current.size, "Insufficient angular coverage")
        // A stable angular maximum is only a hypothesis until physical validation exists.
        return unknown(current.size, "Directional hypotheses require physical validation")
    }

    fun analyze(observations: List<DirectionObservation>): DirectionEvidence {
        val valid = observations.filter { it.orientationDegrees != null }
        val profiles = valid.groupBy { bucket(it.orientationDegrees!!) }.toSortedMap().values.map { bucket ->
            val values = bucket.mapNotNull { it.filteredRssi ?: it.rawRssi?.toDouble() }
            AngularRssiProfile(bucket.first().orientationDegrees!!.let(::normalize), median(values), values.averageOrNull(), variance(values), values.size, bucket.map { it.timestamp })
        }
        val angles = valid.map { normalize(it.orientationDegrees!!) }
        val span = circularCoverage(angles)
        val rssis = valid.mapNotNull { it.filteredRssi ?: it.rawRssi?.toDouble() }
        val ordered = valid.sortedBy { it.timestamp }
        val gradient = if (ordered.size >= 2) (ordered.last().filteredRssi ?: ordered.last().rawRssi?.toDouble())?.minus(ordered.first().filteredRssi ?: ordered.first().rawRssi?.toDouble() ?: 0.0) else null
        return DirectionEvidence(profiles, span, variance(rssis), rssis.size >= minimumSamples && (variance(rssis) ?: Double.MAX_VALUE) < 36.0, gradient, valid.count { it.motionState == MotionState.MOVING })
    }

    fun sectorForHypothesis(bearingDegrees: Double): DirectionSector {
        val a = normalize(bearingDegrees)
        return when { a < 22.5 || a >= 337.5 -> DirectionSector.FRONT; a < 67.5 -> DirectionSector.FRONT_RIGHT; a < 112.5 -> DirectionSector.RIGHT; a < 157.5 -> DirectionSector.BACK_RIGHT; a < 202.5 -> DirectionSector.BACK; a < 247.5 -> DirectionSector.BACK_LEFT; a < 292.5 -> DirectionSector.LEFT; else -> DirectionSector.FRONT_LEFT }
    }

    private fun unknown(count: Int, reason: String) = DirectionEstimate(sampleCount = count, reason = reason)
    private fun bucket(angle: Double) = (normalize(angle) / 22.5).toInt() * 22.5
    private fun normalize(angle: Double): Double = ((angle % 360.0) + 360.0) % 360.0
    private fun median(values: List<Double>): Double? { if (values.isEmpty()) return null; val s = values.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
    private fun variance(values: List<Double>): Double? { if (values.size < 2) return null; val mean = values.average(); return values.map { (it - mean) * (it - mean) }.average() }
    private fun List<Double>.averageOrNull() = takeIf { isNotEmpty() }?.average()
    private fun circularCoverage(angles: List<Double>): Double { if (angles.size < 2) return 0.0; val sorted = angles.distinct().sorted(); val largestGap = sorted.indices.maxOf { i -> val next = if (i == sorted.lastIndex) sorted.first() + 360 else sorted[i + 1]; next - sorted[i] }; return (360.0 - largestGap).coerceIn(0.0, 360.0) }
}

/** In-memory manual experiment session; the caller must supply sensor-derived orientation. */
class DirectionExperimentRecorder(val experimentId: String, val peerId: String) {
    private val _observations = mutableListOf<DirectionObservation>()
    val observations: List<DirectionObservation> get() = _observations.toList()
    fun record(observation: DirectionObservation) {
        require(observation.experimentId == experimentId && observation.peerId == peerId)
        _observations += observation
    }
    fun angularProfile(): List<AngularRssiProfile> = RelativeDirectionEstimator().analyze(_observations).angularProfiles
}

/** Converts a rotation-vector sensor value into device-facing orientation only. It is not peer bearing. */
object RotationVectorOrientation {
    fun headingDegrees(rotationVector: List<Float>): Double? {
        if (rotationVector.size < 4) return null
        return runCatching {
            val matrix = FloatArray(9)
            android.hardware.SensorManager.getRotationMatrixFromVector(matrix, rotationVector.toFloatArray())
            val orientation = android.hardware.SensorManager.getOrientation(matrix, FloatArray(3))
            Math.toDegrees(orientation[0].toDouble()).let { ((it % 360.0) + 360.0) % 360.0 }
        }.getOrNull()
    }
}
