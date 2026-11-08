package com.vokie.ranging

import com.vokie.domain.model.TransportType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tests for measurement replay capability and validation recorder completeness.
 */
class ReplayAndRecorderTest {

    // === Replay tests ===

    // 19. Deterministic distance replay
    @Test fun `replay rssi sequence produces consistent distance estimate`() {
        val estimator = RelativeDistanceEstimator(minimumSamples = 5)
        val calibration = PairCalibration("peer").also {
            it.add(CalibrationPoint("peer", 0.5, -40.0, 1.0, 30, 1))
            it.add(CalibrationPoint("peer", 3.0, -65.0, 2.0, 30, 2))
        }
        val replay = listOf(-50, -52, -48, -50, -51, -49, -50, -50, -51, -50)
        val measurements = replay.mapIndexed { i, rssi ->
            LocalizationMeasurement("peer", i.toLong(), TransportType.BLUETOOTH, LocalizationSource.BLE_RSSI, rssi, rssi.toDouble())
        }
        val r1 = estimator.estimate(measurements, calibration)
        val r2 = estimator.estimate(measurements, calibration) // same input
        assertEquals(r1.estimateMeters, r2.estimateMeters) // deterministic
        assertNotNull(r1.estimateMeters)
    }

    // 20. Deterministic direction replay
    @Test fun `replay angular observations produces consistent analysis`() {
        val estimator = RelativeDirectionEstimator(minimumSamples = 8)
        val observations = (0 until 16).map {
            DirectionObservation("exp", (it + 1).toLong(), "peer", it * 22.5, -50, -50.0, MotionState.STATIONARY)
        }
        val e1 = estimator.analyze(observations)
        val e2 = estimator.analyze(observations)
        assertEquals(e1.angularProfiles.size, e2.angularProfiles.size)
        assertEquals(e1.orientationSpanDegrees, e2.orientationSpanDegrees, 0.001)
    }

    // === Recorder lifecycle tests (JVM-safe, no org.json persistence) ===

    // 21. Recorder lifecycle: experiment/run management
    @Test fun `recorder manages experiment lifecycle`() {
        val dir = createTempDir()
        val recorder = PhysicalValidationRecorder(dir)
        recorder.startExperiment("exp-lifecycle", ValidationExperimentType.DISTANCE)
        assertEquals("exp-lifecycle", recorder.currentExperiment())
        assertEquals(ValidationExperimentType.DISTANCE, recorder.currentType())
        recorder.startRun("run-1")
        assertEquals("run-1", recorder.currentRun())
        assertTrue(recorder.isRecording())
        recorder.stopRun()
        assertFalse(recorder.isRecording())
    }

    // 22. Recorder reset cleans state
    @Test fun `recorder reset clears state`() {
        val dir = createTempDir()
        val recorder = PhysicalValidationRecorder(dir)
        recorder.startExperiment("exp-reset", ValidationExperimentType.DISTANCE)
        recorder.startRun("run-1")
        recorder.stopRun()
        recorder.resetExperiment()
        assertNull(recorder.currentExperiment())
        assertNull(recorder.currentRun())
        assertEquals(0, recorder.currentRunSampleCount.value)
    }

    // 23. Experiment isolation (metadata level)
    @Test fun `experiment types are independent`() {
        val dist = ValidationMeasurement(
            experimentId = "exp-d", runId = "r1", deviceId = "d", peerId = "p", timestamp = 1,
            rawRssi = -50, filteredRssi = -50.0, wifiDirectState = null, bluetoothState = null,
            connectionState = null, transport = null, motionState = null, orientation = null,
            gyro = null, accelerometer = null, magnetometer = null, rotationVector = null,
            groundTruthDistanceMeters = 2.0, experimentType = "DISTANCE")
        val dir = ValidationMeasurement(
            experimentId = "exp-a", runId = "r1", deviceId = "d", peerId = "p", timestamp = 1,
            rawRssi = -50, filteredRssi = -50.0, wifiDirectState = null, bluetoothState = null,
            connectionState = null, transport = null, motionState = null, orientation = null,
            gyro = null, accelerometer = null, magnetometer = null, rotationVector = null,
            groundTruthAngleDegrees = 90.0, experimentType = "DIRECTION")
        assertEquals("DISTANCE", dist.experimentType)
        assertEquals("DIRECTION", dir.experimentType)
        assertNotEquals(dist.experimentId, dir.experimentId)
    }

    // 24. Run isolation (metadata level)
    @Test fun `run ids are distinguishable`() {
        val r1 = validationMeasurement("exp", "run-1")
        val r2 = validationMeasurement("exp", "run-2")
        assertNotEquals(r1.runId, r2.runId)
        assertEquals(r1.experimentId, r2.experimentId)
    }

    // 25. Ground-truth fields preserved in data class
    @Test fun `ground truth fields are preserved in measurement`() {
        val m = validationMeasurement("exp", "run", groundTruth = 2.5)
        assertEquals(2.5, m.groundTruthDistanceMeters!!, 0.001)
        assertNull(m.groundTruthAngleDegrees)
    }

    private fun validationMeasurement(expId: String, runId: String, groundTruth: Double? = 1.0) =
        ValidationMeasurement(
            experimentId = expId, runId = runId, deviceId = "phone-a", peerId = "peer-b",
            timestamp = System.currentTimeMillis(), rawRssi = -50, filteredRssi = -50.0,
            wifiDirectState = "IDLE", bluetoothState = "CONNECTED", connectionState = "CONNECTED",
            transport = "BLUETOOTH", motionState = "STATIONARY", orientation = null, gyro = null,
            accelerometer = null, magnetometer = null, rotationVector = null,
            groundTruthDistanceMeters = groundTruth, estimatedDistanceMeters = null,
            distanceState = "UNKNOWN_RANGE", directionSector = "UNKNOWN", confidence = "LOW",
            experimentType = "DISTANCE", measurementSource = "BLE",
        )

    private fun createTempDir() = File(System.getProperty("java.io.tmpdir"), "vokie-test-${System.nanoTime()}").apply { mkdirs() }
}
