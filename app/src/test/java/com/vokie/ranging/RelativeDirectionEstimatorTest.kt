package com.vokie.ranging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeDirectionEstimatorTest {
    private fun observation(i: Int, angle: Double?, rssi: Int = -50, motion: MotionState = MotionState.STATIONARY) = DirectionObservation("exp", i.toLong(), "peer", angle, rssi, rssi.toDouble(), motion, setOf("accelerometer"))

    @Test fun noMeasurementsIsUnknown() { assertNull(RelativeDirectionEstimator().estimate(emptyList(), 100).bearingDegrees) }
    @Test fun insufficientAngularCoverageIsUnknown() {
        val data = (1..20).map { observation(it, 10.0) }
        assertEquals(DirectionSector.UNKNOWN, RelativeDirectionEstimator().estimate(data, 100).directionSector)
    }
    @Test fun rssiMaximumIsNotAutomaticallyDirection() {
        val data = (0 until 16).map { observation(it + 1, it * 22.5, if (it == 4) -30 else -50) }
        val result = RelativeDirectionEstimator().estimate(data, 100)
        assertNull(result.bearingDegrees)
        assertEquals(DirectionMethod.NONE, result.method)
    }
    @Test fun wraparoundCoverageAndSectorNormalize() {
        val estimator = RelativeDirectionEstimator()
        assertEquals(DirectionSector.FRONT, estimator.sectorForHypothesis(359.0))
        assertEquals(DirectionSector.FRONT, estimator.sectorForHypothesis(-1.0))
    }
    @Test fun staleObservationsAreRejected() {
        val data = (1..30).map { observation(it, (it * 12.0) % 360) }
        assertEquals(DirectionSector.UNKNOWN, RelativeDirectionEstimator(staleAfterMs = 10).estimate(data, 100).directionSector)
    }
    @Test fun motionStatesRemainSensorEvidenceOnly() {
        val classifier = MotionClassifier()
        assertEquals(MotionState.STATIONARY, classifier.classify(listOfNotNull(LocalizationMeasurement("p", 1, com.vokie.domain.model.TransportType.BLUETOOTH, LocalizationSource.BLE_RSSI, motionSnapshot = DeviceMotionSnapshot(1, angularVelocity = listOf(0f, 0f, 0f), acceleration = listOf(0f, 0f, 0f))))))
        assertEquals(MotionState.ROTATING, classifier.classify(listOfNotNull(LocalizationMeasurement("p", 1, com.vokie.domain.model.TransportType.BLUETOOTH, LocalizationSource.BLE_RSSI, motionSnapshot = DeviceMotionSnapshot(1, angularVelocity = listOf(1f, 0f, 0f))))))
    }
}
