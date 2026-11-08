package com.vokie.ranging

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** DEBUG-only, incremental recorder. Ground truth is user-entered and never inferred. */
enum class ValidationExperimentType { DISTANCE, DIRECTION, ROTATION, ARC_MOVEMENT }

data class ValidationMeasurement(
    val experimentId: String, val runId: String, val deviceId: String, val peerId: String, val timestamp: Long,
    val rawRssi: Int?, val filteredRssi: Double?, val wifiDirectState: String?, val bluetoothState: String?, val connectionState: String?, val transport: String?, val motionState: String?,
    val orientation: List<Float>?, val gyro: List<Float>?, val accelerometer: List<Float>?, val magnetometer: List<Float>?, val rotationVector: List<Float>?,
    val groundTruthDistanceMeters: Double? = null, val groundTruthAngleDegrees: Double? = null, val estimatedDistanceMeters: Double? = null, val estimatedBearingDegrees: Double? = null,
    val distanceState: String? = null, val directionSector: String? = null, val confidence: String? = null, val uncertaintyMeters: Double? = null, val uncertaintyDegrees: Double? = null,
    val sampleCount: Int = 1, val experimentType: String = ValidationExperimentType.DISTANCE.name, val measurementSource: String? = null, val wifiRttAvailable: Boolean? = null,
    val deviceModel: String? = null, val androidSdk: Int? = null, val localRole: String? = null, val ackRttMs: Long? = null, val messageId: String? = null,
)

class PhysicalValidationRecorder(private val root: File) {
    private val distanceFile get() = root.resolve("distance_validation_measurements.json")
    private val directionFile get() = root.resolve("direction_validation_measurements.json")
    private val summaryFile get() = root.resolve("validation_summary.json")
    private var experimentId: String? = null
    private var experimentType: ValidationExperimentType? = null
    private var runId: String? = null
    private var recording = false
    private val _currentRunSampleCount = MutableStateFlow(0)
    val currentRunSampleCount: StateFlow<Int> = _currentRunSampleCount.asStateFlow()
    var lastError: String? = null; private set
    init { root.mkdirs() }

    @Synchronized fun startExperiment(id: String, type: ValidationExperimentType) {
        require(id.isNotBlank()) { "Experiment ID is required" }; experimentId = id; experimentType = type; runId = null; recording = false; log("EXPERIMENT_STARTED id=$id type=$type")
    }
    @Synchronized fun startRun(id: String) {
        require(experimentId != null && experimentType != null) { "Start an experiment first" }; require(id.isNotBlank()) { "Run ID is required" }
        runId = id
        _currentRunSampleCount.value = countForRun(id)
        recording = true
        log("RUN_STARTED run=$id samples=${_currentRunSampleCount.value}")
    }
    @Synchronized fun stopRun() { recording = false; log("RUN_STOPPED run=${runId ?: "unknown"} samples=${_currentRunSampleCount.value}") }
    @Synchronized fun finalizeExperiment() { recording = false; writeSummary(); log("EXPERIMENT_FINALIZED id=${experimentId ?: "unknown"}") }
    @Synchronized fun resetExperiment() { experimentId = null; experimentType = null; runId = null; recording = false; _currentRunSampleCount.value = 0; log("RESET") }
    fun isRecording() = recording
    fun currentExperiment() = experimentId
    fun currentRun() = runId
    fun currentType() = experimentType

    @Synchronized fun recordSample(measurement: ValidationMeasurement) {
        require(recording) { "No active run" }; require(measurement.experimentId == experimentId && measurement.runId == runId) { "Sample metadata does not match active run" }; validate(measurement)
        val target = if (experimentType in setOf(ValidationExperimentType.DIRECTION, ValidationExperimentType.ROTATION, ValidationExperimentType.ARC_MOVEMENT)) directionFile else distanceFile
        try { append(target, measurement.toJson()) } catch (t: Throwable) { lastError = t.message ?: "Persistence error"; log("PERSISTENCE_ERROR ${lastError}"); throw t }
        _currentRunSampleCount.value = countForRun(measurement.runId)
        log("SAMPLE_RECORDED run=${measurement.runId} sample=${_currentRunSampleCount.value} peer=${measurement.peerId} rssi=${measurement.rawRssi} filtered=${measurement.filteredRssi} distance=${measurement.estimatedDistanceMeters ?: "UNKNOWN"} direction=${measurement.directionSector ?: "UNKNOWN"}")
    }
    fun recordDistance(measurement: ValidationMeasurement) { require(measurement.groundTruthAngleDegrees == null); recordSample(measurement.copy(experimentType = ValidationExperimentType.DISTANCE.name)) }
    fun recordDirection(measurement: ValidationMeasurement) { require(measurement.groundTruthDistanceMeters == null); recordSample(measurement.copy(experimentType = ValidationExperimentType.DIRECTION.name)) }
    fun readDistance(): List<JSONObject> = read(distanceFile)
    fun readDirection(): List<JSONObject> = read(directionFile)

    private fun validate(m: ValidationMeasurement) {
        require(m.experimentId.isNotBlank() && m.runId.isNotBlank() && m.peerId.isNotBlank() && m.deviceId.isNotBlank())
        require(m.rawRssi == null || m.rawRssi in -127..0) { "Invalid RSSI" }
        require(m.sampleCount >= 1) { "Sample count must be positive" }
        require(m.groundTruthDistanceMeters == null || m.groundTruthDistanceMeters > 0) { "Invalid distance" }
        require(m.groundTruthAngleDegrees == null || m.groundTruthAngleDegrees in 0.0..359.0) { "Invalid angle" }
    }
    private fun append(file: File, value: JSONObject) { val array = JSONArray(if (file.exists()) file.readText() else "[]"); array.put(value); val temp = file.resolveSibling(".${file.name}.tmp"); temp.writeText(array.toString(2) + "\n"); if (!temp.renameTo(file)) { file.delete(); check(temp.renameTo(file)) { "Could not commit ${file.name}" } } }
    private fun read(file: File): List<JSONObject> { if (!file.exists()) return emptyList(); val a = JSONArray(file.readText()); return (0 until a.length()).map { a.getJSONObject(it) } }
    private fun countForRun(id: String): Int {
        val file = if (experimentType in setOf(ValidationExperimentType.DIRECTION, ValidationExperimentType.ROTATION, ValidationExperimentType.ARC_MOVEMENT)) directionFile else distanceFile
        return read(file).count { it.optString("experimentId") == experimentId && it.optString("runId") == id }
    }
    private fun writeSummary() { val summary = JSONObject().put("experimentId", experimentId).put("experimentType", experimentType?.name).put("generatedAt", System.currentTimeMillis()).put("realMeasurementsOnly", true).put("distanceRecordCount", readDistance().size).put("directionRecordCount", readDirection().size); val temp = summaryFile.resolveSibling(".${summaryFile.name}.tmp"); temp.writeText(summary.toString(2) + "\n"); if (!temp.renameTo(summaryFile)) { summaryFile.delete(); temp.renameTo(summaryFile) } }
    private fun log(message: String) = runCatching { android.util.Log.i("VOKIE_VALIDATION", message) }
}

private fun ValidationMeasurement.toJson() = JSONObject().apply {
    put("experimentId", experimentId); put("runId", runId); put("deviceId", deviceId); put("peerId", peerId); put("timestamp", timestamp)
    putNullable("rawRssi", rawRssi); putNullable("filteredRssi", filteredRssi); putNullable("wifiDirectState", wifiDirectState); putNullable("bluetoothState", bluetoothState); putNullable("connectionState", connectionState); putNullable("transport", transport); putNullable("motionState", motionState)
    putNullable("orientation", orientation); putNullable("gyroscope", gyro); putNullable("accelerometer", accelerometer); putNullable("magnetometer", magnetometer); putNullable("rotationVector", rotationVector)
    putNullable("groundTruthDistanceMeters", groundTruthDistanceMeters); putNullable("groundTruthAngleDegrees", groundTruthAngleDegrees); putNullable("estimatedDistanceMeters", estimatedDistanceMeters); putNullable("estimatedBearingDegrees", estimatedBearingDegrees)
    putNullable("distanceState", distanceState); putNullable("directionSector", directionSector); putNullable("confidence", confidence); putNullable("uncertaintyMeters", uncertaintyMeters); putNullable("uncertaintyDegrees", uncertaintyDegrees); put("sampleCount", sampleCount)
    put("experimentType", experimentType); putNullable("measurementSource", measurementSource); putNullable("wifiRttAvailable", wifiRttAvailable); putNullable("deviceModel", deviceModel); putNullable("androidSdk", androidSdk); putNullable("localRole", localRole); putNullable("ackRttMs", ackRttMs); putNullable("messageId", messageId)
}
private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
