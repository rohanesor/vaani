package com.vokie.ranging

import kotlin.math.sqrt

/** Output of the research estimator. A number is emitted only after pair calibration. */
enum class DistanceMethod { NONE, CALIBRATED_BLE, WIFI_RTT }
enum class DistanceRange { UNKNOWN, VERY_NEAR, NEAR, MEDIUM_RANGE, FAR, UNKNOWN_RANGE }
enum class MotionState { STATIONARY, MOVING, ROTATING, UNKNOWN }

data class DistanceEstimate(
    val estimateMeters: Double? = null,
    val lowerBoundMeters: Double? = null,
    val upperBoundMeters: Double? = null,
    val confidence: LocalizationConfidence = LocalizationConfidence.LOW,
    val method: DistanceMethod = DistanceMethod.NONE,
    val sampleCount: Int = 0,
    val timestamp: Long = 0L,
    val reliability: Double = 0.0,
    val range: DistanceRange = DistanceRange.UNKNOWN_RANGE,
    val reason: String? = null,
)

data class DeviceRadioProfile(
    val manufacturer: String,
    val model: String,
    val bleChipset: String? = null,
    val referenceRssiMean: Double? = null,
    val referenceRssiStandardDeviation: Double? = null,
    val calibrationStatus: String = "NOT_CALIBRATED",
    val calibrationTimestamp: Long? = null,
)

data class CalibrationPoint(
    val pairId: String,
    val approximateExperimentalSeparationMeters: Double,
    val rssiMedian: Double,
    val rssiStandardDeviation: Double,
    val sampleCount: Int,
    val timestamp: Long,
)

/** User-assisted, device-pair calibration. The separation is supplied by the user, never inferred. */
class PairCalibration(private val pairId: String) {
    private val _points = mutableListOf<CalibrationPoint>()
    val points: List<CalibrationPoint> get() = _points.toList()
    fun add(point: CalibrationPoint) {
        require(point.pairId == pairId && point.approximateExperimentalSeparationMeters > 0)
        _points.removeAll { it.approximateExperimentalSeparationMeters == point.approximateExperimentalSeparationMeters }
        _points += point
        _points.sortBy { it.rssiMedian }
    }
}

class MotionClassifier {
    fun classify(history: List<LocalizationMeasurement>): MotionState {
        val snapshots = history.mapNotNull { it.motionSnapshot }
        if (snapshots.isEmpty()) return MotionState.UNKNOWN
        val rotating = snapshots.any { magnitude(it.angularVelocity) > ROTATION_THRESHOLD }
        val moving = snapshots.any { magnitude(it.acceleration) > ACCELERATION_THRESHOLD }
        return when { moving -> MotionState.MOVING; rotating -> MotionState.ROTATING; else -> MotionState.STATIONARY }
    }
    private fun magnitude(v: List<Float>?): Double = v?.let { sqrt(it.sumOf { x -> x.toDouble() * x }) } ?: 0.0
    private companion object { const val ROTATION_THRESHOLD = .15; const val ACCELERATION_THRESHOLD = .35 }
}

/** RSSI is mapped only through user-supplied, pair-specific calibration points. */
class RelativeDistanceEstimator(private val minimumSamples: Int = 5) {
    fun estimate(history: List<LocalizationMeasurement>, calibration: PairCalibration?): DistanceEstimate {
        val latest = history.maxByOrNull { it.timestamp } ?: return unavailable("No measurements")
        val rssis = history.mapNotNull { it.bleRssiFiltered ?: it.bleRssiRaw?.toDouble() }
        if (calibration == null || calibration.points.size < 2) return unavailable("No validated calibration", latest.timestamp, rssis.size)
        if (rssis.size < minimumSamples) return unavailable("Insufficient RSSI samples", latest.timestamp, rssis.size)
        val points = calibration.points
        val rssi = median(rssis.takeLast(10))
        val ordered = points.sortedBy { it.rssiMedian }
        val estimate = interpolate(rssi, ordered)
        val spread = standardDeviation(rssis).coerceAtLeast(.25)
        val calibrationError = ordered.map { kotlin.math.abs(it.rssiStandardDeviation) }.average().coerceAtLeast(.25)
        val uncertainty = (spread + calibrationError).coerceAtLeast(.5)
        val reliability = (1.0 - uncertainty / 12.0).coerceIn(0.0, 1.0)
        val confidence = when { rssis.size >= 30 && reliability >= .75 -> LocalizationConfidence.HIGH; rssis.size >= 10 && reliability >= .4 -> LocalizationConfidence.MEDIUM; else -> LocalizationConfidence.LOW }
        return DistanceEstimate(estimate, (estimate - uncertainty).coerceAtLeast(0.0), estimate + uncertainty, confidence, DistanceMethod.CALIBRATED_BLE, rssis.size, latest.timestamp, reliability, rangeFor(estimate, ordered), "Pair-calibrated RSSI; not universal")
    }
    private fun unavailable(reason: String, timestamp: Long = 0, samples: Int = 0) = DistanceEstimate(timestamp = timestamp, sampleCount = samples, reason = reason)
    private fun interpolate(x: Double, p: List<CalibrationPoint>): Double {
        if (x <= p.first().rssiMedian) return p.first().approximateExperimentalSeparationMeters
        if (x >= p.last().rssiMedian) return p.last().approximateExperimentalSeparationMeters
        val hi = p.indexOfFirst { it.rssiMedian >= x }; val a = p[hi - 1]; val b = p[hi]
        val fraction = (x - a.rssiMedian) / (b.rssiMedian - a.rssiMedian)
        return a.approximateExperimentalSeparationMeters + fraction * (b.approximateExperimentalSeparationMeters - a.approximateExperimentalSeparationMeters)
    }
    private fun rangeFor(value: Double, p: List<CalibrationPoint>): DistanceRange = when {
        value <= p.minOf { it.approximateExperimentalSeparationMeters } -> DistanceRange.VERY_NEAR
        value >= p.maxOf { it.approximateExperimentalSeparationMeters } -> DistanceRange.FAR
        else -> DistanceRange.MEDIUM_RANGE
    }
    private fun median(values: List<Double>): Double { val s = values.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
    private fun standardDeviation(v: List<Double>): Double { val mean = v.average(); return sqrt(v.map { (it - mean) * (it - mean) }.average()) }
}

data class DistanceTrack(val values: List<DistanceEstimate> = emptyList()) {
    fun append(next: DistanceEstimate): DistanceTrack = copy(values = (values + next).takeLast(30))
    val latest: DistanceEstimate? get() = values.lastOrNull()
}
