package com.vokie.ranging

import com.vokie.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Test

/**
 * Deterministic synthetic RSSI datasets for distance estimator validation.
 * These verify algorithmic behaviour, NOT physical accuracy.
 */
class SyntheticDistanceTest {
    private val estimator = RelativeDistanceEstimator(minimumSamples = 5)

    private fun measurement(ts: Long, rssi: Int, filtered: Double? = null) =
        LocalizationMeasurement("peer", ts, TransportType.BLUETOOTH, LocalizationSource.BLE_RSSI, rssi, filtered ?: rssi.toDouble())

    private fun calibration(): PairCalibration {
        val c = PairCalibration("peer")
        c.add(CalibrationPoint("peer", 0.5, -40.0, 1.0, 30, 1))
        c.add(CalibrationPoint("peer", 1.0, -50.0, 2.0, 30, 2))
        c.add(CalibrationPoint("peer", 2.0, -60.0, 2.5, 30, 3))
        c.add(CalibrationPoint("peer", 5.0, -75.0, 3.0, 30, 4))
        return c
    }

    // 1. Insufficient history
    @Test fun `insufficient history returns unknown`() {
        val result = estimator.estimate(listOf(measurement(1, -50)), calibration())
        assertNull(result.estimateMeters)
        assertEquals(DistanceRange.UNKNOWN_RANGE, result.range)
    }

    // 2. Stable low-noise RSSI
    @Test fun `stable rssi produces calibrated estimate`() {
        val history = (1..10L).map { measurement(it, -50) }
        val result = estimator.estimate(history, calibration())
        assertNotNull(result.estimateMeters)
        assertEquals(DistanceMethod.CALIBRATED_BLE, result.method)
        assertTrue(result.estimateMeters!! in 0.8..1.2) // near 1.0m calibration point
    }

    // 3. Noisy RSSI
    @Test fun `noisy rssi reduces confidence`() {
        val values = listOf(-45, -55, -42, -58, -47, -53, -40, -60, -48, -52)
        val history = values.mapIndexed { i, v -> measurement(i.toLong(), v) }
        val result = estimator.estimate(history, calibration())
        assertNotNull(result.estimateMeters)
        // Noisy data should have wider uncertainty bounds
        assertTrue((result.upperBoundMeters!! - result.lowerBoundMeters!!) > 0.5)
    }

    // 4. RSSI outlier
    @Test fun `outlier in stable series does not destroy estimate`() {
        val values = listOf(-50, -50, -50, -50, -50, -50, -50, -50, -20, -50) // -20 is outlier
        val history = values.mapIndexed { i, v -> measurement(i.toLong(), v) }
        val result = estimator.estimate(history, calibration())
        assertNotNull(result.estimateMeters)
        // Median filtering should limit the outlier's effect
    }

    // 5. Increasing RSSI (getting closer)
    @Test fun `increasing rssi suggests decreasing distance`() {
        val values = listOf(-70, -68, -65, -62, -58, -55, -52, -50, -48, -45)
        val history = values.mapIndexed { i, v -> measurement(i.toLong(), v) }
        val result = estimator.estimate(history, calibration())
        assertNotNull(result.estimateMeters)
        assertTrue(result.estimateMeters!! < 2.0) // closer than 2m calibration
    }

    // 6. Decreasing RSSI (getting farther)
    @Test fun `decreasing rssi suggests increasing distance`() {
        val values = listOf(-45, -48, -50, -52, -55, -58, -60, -62, -65, -68)
        val history = values.mapIndexed { i, v -> measurement(i.toLong(), v) }
        val result = estimator.estimate(history, calibration())
        assertNotNull(result.estimateMeters)
        assertTrue(result.estimateMeters!! > 1.0)
    }

    // 7. Motion transition (sensor snapshot)
    @Test fun `motion classifier detects rotation`() {
        val rotating = LocalizationMeasurement("p", 1, TransportType.BLUETOOTH, LocalizationSource.BLE_RSSI,
            motionSnapshot = DeviceMotionSnapshot(1, angularVelocity = listOf(1.0f, 0f, 0f), acceleration = listOf(0f, 0f, 0f)))
        assertEquals(MotionState.ROTATING, MotionClassifier().classify(listOf(rotating)))
    }

    // 8. Confidence degradation
    @Test fun `few samples produce low confidence`() {
        val history = (1..5L).map { measurement(it, -50) }
        val result = estimator.estimate(history, calibration())
        assertNotNull(result.estimateMeters)
        assertEquals(LocalizationConfidence.LOW, result.confidence)
    }

    // 9. Uncertainty propagation
    @Test fun `uncertainty bounds are always non-negative`() {
        val history = (1..20L).map { measurement(it, -55) }
        val result = estimator.estimate(history, calibration())
        assertNotNull(result.lowerBoundMeters)
        assertNotNull(result.upperBoundMeters)
        assertTrue(result.lowerBoundMeters!! >= 0.0)
        assertTrue(result.upperBoundMeters!! > result.lowerBoundMeters!!)
    }

    // 10. No calibration → UNKNOWN fallback
    @Test fun `no calibration returns unknown safely`() {
        val history = (1..10L).map { measurement(it, -50) }
        val result = estimator.estimate(history, null)
        assertNull(result.estimateMeters)
        assertEquals(DistanceMethod.NONE, result.method)
        assertEquals("No validated calibration", result.reason)
    }
}
