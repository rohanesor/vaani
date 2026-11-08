package com.vokie.ranging

import org.junit.Assert.*
import org.junit.Test

/**
 * Deterministic angular RSSI profiles for direction estimator validation.
 * These verify algorithmic behaviour, NOT physical direction accuracy.
 */
class SyntheticDirectionTest {
    private val estimator = RelativeDirectionEstimator(minimumSamples = 16, minimumAngularCoverageDegrees = 270.0)

    private fun obs(i: Int, angle: Double, rssi: Int = -50, motion: MotionState = MotionState.STATIONARY) =
        DirectionObservation("exp", i.toLong(), "peer", angle, rssi, rssi.toDouble(), motion)

    // 11. Clear angular maximum
    @Test fun `clear angular maximum produces evidence`() {
        val data = (0 until 16).map { obs(it + 1, it * 22.5, if (it == 4) -30 else -55) }
        val evidence = estimator.analyze(data)
        val maxProfile = evidence.angularProfiles.maxByOrNull { it.rssiMedian ?: Double.MIN_VALUE }
        assertNotNull(maxProfile)
        assertTrue(maxProfile!!.rssiMedian!! > -50)
    }

    // 12. Noisy angular profile
    @Test fun `noisy angular profile produces broad uncertainty`() {
        val data = (0 until 16).map { obs(it + 1, it * 22.5, (-50 + (it % 5) * 3 - 6)) }
        val evidence = estimator.analyze(data)
        assertNotNull(evidence.rssVariance)
        assertTrue(evidence.rssVariance!! > 5.0) // noisy
    }

    // 13. Multiple maxima
    @Test fun `multiple maxima detected in profile`() {
        val data = (0 until 16).map {
            val rssi = if (it == 2 || it == 10) -30 else -55
            obs(it + 1, it * 22.5, rssi)
        }
        val evidence = estimator.analyze(data)
        val strong = evidence.angularProfiles.filter { (it.rssiMedian ?: -100.0) > -40 }
        assertTrue(strong.size >= 2) // two separate maxima
    }

    // 14. Insufficient angular coverage
    @Test fun `insufficient angular coverage returns unknown`() {
        val data = (1..20).map { obs(it, 10.0 + it * 2.0) } // narrow arc ~40°
        val result = estimator.estimate(data, 100)
        assertEquals(DirectionSector.UNKNOWN, result.directionSector)
        assertNull(result.bearingDegrees)
    }

    // 15. Rotation state
    @Test fun `rotation state detected from sensor data`() {
        val classifier = MotionClassifier()
        val rotating = LocalizationMeasurement("p", 1, com.vokie.domain.model.TransportType.BLUETOOTH,
            LocalizationSource.BLE_RSSI, motionSnapshot = DeviceMotionSnapshot(1,
                angularVelocity = listOf(0.5f, 0f, 0f), acceleration = listOf(0f, 0f, 0f)))
        assertEquals(MotionState.ROTATING, classifier.classify(listOf(rotating)))
    }

    // 16. Stationary state
    @Test fun `stationary state detected from sensor data`() {
        val classifier = MotionClassifier()
        val stationary = LocalizationMeasurement("p", 1, com.vokie.domain.model.TransportType.BLUETOOTH,
            LocalizationSource.BLE_RSSI, motionSnapshot = DeviceMotionSnapshot(1,
                angularVelocity = listOf(0f, 0f, 0f), acceleration = listOf(0f, 0f, 0f)))
        assertEquals(MotionState.STATIONARY, classifier.classify(listOf(stationary)))
    }

    // 17. Confidence degradation
    @Test fun `direction always returns low confidence without validation`() {
        val data = (0 until 16).map { obs(it + 1, it * 22.5) }
        val result = estimator.estimate(data, 100)
        assertEquals(LocalizationConfidence.LOW, result.confidence)
    }

    // 18. UNKNOWN fallback
    @Test fun `default is unknown without physical validation`() {
        val data = (0 until 20).map { obs(it + 1, it * 18.0) }
        val result = estimator.estimate(data, 100)
        assertEquals(DirectionSector.UNKNOWN, result.directionSector)
        assertNull(result.bearingDegrees)
        assertTrue(result.reason.contains("validation"))
    }
}
