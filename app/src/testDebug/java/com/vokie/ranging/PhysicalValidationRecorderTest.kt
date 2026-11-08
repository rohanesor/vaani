package com.vokie.ranging

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalValidationRecorderTest {
    private fun measurement(rssi: Int? = -50) = ValidationMeasurement(
        "exp", "run-1", "phone-a", "peer", 1L, rssi, -50.0,
        "IDLE", "CONNECTED", "CONNECTED", "BLUETOOTH", "STATIONARY", null, null, null, null, null,
        groundTruthDistanceMeters = 1.0,
    )
    @Test fun validationMeasurementKeepsGroundTruthSeparate() {
        val value = measurement()
        assertEquals(1.0, value.groundTruthDistanceMeters!!, 0.0)
        assertEquals(null, value.groundTruthAngleDegrees)
    }
    @Test fun runCountIsOwnedByRecorderAndResettable() {
        val recorder = PhysicalValidationRecorder(createTempDir())
        recorder.startExperiment("exp", ValidationExperimentType.DISTANCE)
        recorder.startRun("run-1")
        assertEquals(0, recorder.currentRunSampleCount.value)
        recorder.resetExperiment()
        assertEquals(0, recorder.currentRunSampleCount.value)
    }
    @Test fun rejectsInvalidRssi() {
        val recorder = PhysicalValidationRecorder(createTempDir())
        recorder.startExperiment("exp", ValidationExperimentType.DISTANCE)
        recorder.startRun("run-1")
        assertTrue(runCatching { recorder.recordDistance(measurement(1)) }.isFailure)
    }
    @Test fun directionAndDistanceLabelsAreIndependent() {
        val value = measurement().copy(groundTruthDistanceMeters = null, groundTruthAngleDegrees = 90.0)
        assertEquals(90.0, value.groundTruthAngleDegrees!!, 0.0)
        assertEquals(null, value.groundTruthDistanceMeters)
    }
    private fun createTempDir() = File(System.getProperty("java.io.tmpdir"), "vokie-validation-${System.nanoTime()}").apply { mkdirs() }
}
