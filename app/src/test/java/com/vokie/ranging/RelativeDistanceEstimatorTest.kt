package com.vokie.ranging

import com.vokie.domain.model.TransportType
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeDistanceEstimatorTest {
    private fun sample(i: Int, value: Int = -50) = LocalizationMeasurement("pair", i.toLong(), TransportType.BLUETOOTH, LocalizationSource.BLE_RSSI, value, value.toDouble())
    @Test fun noMeasurementsDoesNotInventDistance() { assertNull(RelativeDistanceEstimator().estimate(emptyList(), null).estimateMeters) }
    @Test fun oneSampleNeedsCalibrationAndMoreEvidence() { assertNull(RelativeDistanceEstimator().estimate(listOf(sample(1)), null).estimateMeters) }
    @Test fun missingRttFallsBackToCalibratedBleOnly() {
        val calibration = PairCalibration("pair")
        calibration.add(CalibrationPoint("pair", .5, -45.0, 1.0, 30, 1))
        calibration.add(CalibrationPoint("pair", 3.0, -65.0, 1.0, 30, 2))
        val result = RelativeDistanceEstimator().estimate((1..10).map { sample(it, -55) }, calibration)
        assertEquals(DistanceMethod.CALIBRATED_BLE, result.method)
    }
    @Test fun calibrationIsPairSpecific() {
        val calibration = PairCalibration("other")
        val thrown = runCatching { calibration.add(CalibrationPoint("pair", 1.0, -50.0, 1.0, 10, 1)) }.isFailure
        assertEquals(true, thrown)
    }
    @Test fun missingSensorsProduceUnknownMotion() { assertEquals(MotionState.UNKNOWN, MotionClassifier().classify(listOf(sample(1)))) }
}
