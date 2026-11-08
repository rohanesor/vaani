package com.vokie.ranging

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/** Evidence-only peer localization. It deliberately has no GPS, distance model, or bearing solver. */
enum class ProximityState { UNKNOWN, VERY_NEAR, NEAR, FAR, STALE }
enum class LocalizationConfidence { LOW, MEDIUM, HIGH }
enum class LocalizationSource { BLE_RSSI, WIFI, ACK_RTT, MESSAGE, PEER_AVAILABLE }
enum class DirectionSector { FRONT, FRONT_RIGHT, RIGHT, BACK_RIGHT, BACK, BACK_LEFT, LEFT, FRONT_LEFT, UNKNOWN }

data class DeviceMotionSnapshot(
    val timestamp: Long,
    val orientation: List<Float>? = null,
    val acceleration: List<Float>? = null,
    val angularVelocity: List<Float>? = null,
    val magneticField: List<Float>? = null,
    val availableSensors: Set<String> = emptySet(),
) {
    fun has(sensor: String) = sensor in availableSensors
}

data class RssiStatistics(
    val latest: Int?, val last10: List<Int>, val last30: List<Int>, val median: Double?,
    val mean: Double?, val minimum: Int?, val maximum: Int?, val standardDeviation: Double?, val sampleCount: Int,
    val timestamp: Long?,
)

data class LocalizationMeasurement(
    val peerId: String, val timestamp: Long, val transport: TransportType,
    val source: LocalizationSource, val bleRssiRaw: Int? = null, val bleRssiFiltered: Double? = null,
    val wifiMeasurement: Double? = null, val ackRttMs: Long? = null,
    val orientation: List<Float>? = null, val motionSnapshot: DeviceMotionSnapshot? = null,
    val sourceQuality: Double = 0.0,
)

data class LocalizationState(
    val peerId: String, val lastSeen: Long, val transport: TransportType,
    val proximityState: ProximityState, val proximityAvailable: Boolean,
    val rangeEstimate: Double? = null, val directionEstimate: Double? = null,
    val bearingDegrees: Double? = null, val directionSector: DirectionSector? = DirectionSector.UNKNOWN,
    val confidence: LocalizationConfidence, val measurementCount: Int,
    val rssi: RssiStatistics? = null, val filteredRssi: Double? = null,
    val lastMeasurement: LocalizationMeasurement? = null,
)

class RelativePeerLocalizationEngine(context: Context, private val staleAfterMs: Long = 30_000L) : SensorEventListener {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val histories = mutableMapOf<String, MutableList<Pair<Long, Int>>>()
    private val measurements = mutableMapOf<String, MutableList<LocalizationMeasurement>>()
    private val ema = mutableMapOf<String, Double>()
    private val _states = MutableStateFlow<Map<String, LocalizationState>>(emptyMap())
    val states: StateFlow<Map<String, LocalizationState>> = _states.asStateFlow()
    private var latestMotion = DeviceMotionSnapshot(0L)
    private val sensorValues = mutableMapOf<Int, List<Float>>()
    private val store = File(context.filesDir, "model-lab/ranging/localization_measurements.json")

    /** Retains identity and starts sensor collection on the first peer observation. */
    @Synchronized fun startPeer(peerId: String, transport: TransportType, timestamp: Long = System.currentTimeMillis()) {
        if (peerId.isBlank()) return
        if (peerId !in _states.value) {
            registerSensors()
            val observation = LocalizationMeasurement(peerId, timestamp, transport, LocalizationSource.PEER_AVAILABLE, motionSnapshot = latestMotion, sourceQuality = 0.2)
            addMeasurement(observation)
            persist(observation)
        }
    }

    @Synchronized fun recordRssi(peerId: String, rssi: Int, transport: TransportType = TransportType.BLUETOOTH, timestamp: Long = System.currentTimeMillis()) {
        startPeer(peerId, transport, timestamp)
        val history = histories.getOrPut(peerId) { mutableListOf() }
        history += timestamp to rssi
        while (history.size > 30) history.removeAt(0)
        val window = history.takeLast(5).map { it.second }.sorted()
        val median = window[window.size / 2].toDouble()
        ema[peerId] = ema[peerId]?.let { it * .75 + median * .25 } ?: median
        val measurement = LocalizationMeasurement(peerId, timestamp, transport, LocalizationSource.BLE_RSSI, rssi, ema[peerId], motionSnapshot = latestMotion, orientation = latestMotion.orientation, sourceQuality = quality(history.map { it.second }))
        addMeasurement(measurement)
        persist(measurement)
    }

    fun recordWifi(peerId: String, value: Double? = null, timestamp: Long = System.currentTimeMillis()) = record(LocalizationMeasurement(peerId, timestamp, TransportType.WIFI_DIRECT, LocalizationSource.WIFI, wifiMeasurement = value, motionSnapshot = latestMotion, sourceQuality = if (value != null) 0.7 else 0.3))
    fun recordMessage(peerId: String, transport: TransportType, timestamp: Long = System.currentTimeMillis()) = record(LocalizationMeasurement(peerId, timestamp, transport, LocalizationSource.MESSAGE, motionSnapshot = latestMotion, sourceQuality = 0.3))
    fun recordAck(peerId: String, transport: TransportType, rttMs: Long?, timestamp: Long = System.currentTimeMillis()) = record(LocalizationMeasurement(peerId, timestamp, transport, LocalizationSource.ACK_RTT, ackRttMs = rttMs, motionSnapshot = latestMotion, sourceQuality = 0.4))
    fun record(measurement: LocalizationMeasurement) { synchronized(this) { startPeer(measurement.peerId, measurement.transport, measurement.timestamp); addMeasurement(measurement); persist(measurement) } }

    @Synchronized fun refresh(now: Long = System.currentTimeMillis()) { _states.value = _states.value.mapValues { (id, state) -> if (now - state.lastSeen > staleAfterMs) state.copy(proximityState = ProximityState.STALE, proximityAvailable = false) else state } }
    fun stopSensors() { sensorManager?.unregisterListener(this) }

    private fun addMeasurement(m: LocalizationMeasurement) {
        val list = measurements.getOrPut(m.peerId) { mutableListOf() }; list += m
        val h = histories[m.peerId].orEmpty(); updateState(m.peerId, m.transport, m.timestamp, m)
    }
    private fun updateState(id: String, transport: TransportType, timestamp: Long, m: LocalizationMeasurement?) {
        val h = histories[id].orEmpty().map { it.second }
        val valid = h.isNotEmpty()
        _states.value = _states.value + (id to LocalizationState(
            peerId = id, lastSeen = timestamp, transport = transport,
            proximityState = ProximityState.UNKNOWN, proximityAvailable = valid,
            confidence = confidence(h, timestamp), measurementCount = measurements[id]?.size ?: 0,
            rssi = rssiStats(id), filteredRssi = ema[id],
            lastMeasurement = m ?: _states.value[id]?.lastMeasurement,
        ))
    }
    private fun rssiStats(id: String): RssiStatistics? { val h = histories[id].orEmpty(); if (h.isEmpty()) return null; val all = h.map { it.second }; val sorted = all.sorted(); val mean = all.average(); val sd = sqrt(all.map { (it - mean) * (it - mean) }.average()); return RssiStatistics(all.last(), all.takeLast(10), all, sorted[sorted.size / 2].toDouble(), mean, all.minOrNull(), all.maxOrNull(), sd, all.size, h.last().first) }
    private fun confidence(h: List<Int>, timestamp: Long): LocalizationConfidence = when { h.size >= 10 && quality(h) >= .75 -> LocalizationConfidence.HIGH; h.size >= 3 -> LocalizationConfidence.MEDIUM; else -> LocalizationConfidence.LOW }
    private fun quality(h: List<Int>): Double { if (h.size < 2) return .2; val sd = sqrt(h.map { (it - h.average()) * (it - h.average()) }.average()); return (1.0 - sd / 20.0).coerceIn(0.0, 1.0) }

    private fun registerSensors() { val sm = sensorManager ?: return; listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION).forEach { sm.getDefaultSensor(it)?.let { sensor -> sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) } } }
    override fun onSensorChanged(event: SensorEvent) {
        sensorValues[event.sensor.type] = event.values.toList()
        val names = sensorValues.keys.mapNotNull { sensorName(it) }.toSet()
        latestMotion = DeviceMotionSnapshot(
            timestamp = System.currentTimeMillis(),
            orientation = sensorValues[Sensor.TYPE_ROTATION_VECTOR] ?: sensorValues[Sensor.TYPE_GAME_ROTATION_VECTOR],
            acceleration = sensorValues[Sensor.TYPE_LINEAR_ACCELERATION] ?: sensorValues[Sensor.TYPE_ACCELEROMETER],
            angularVelocity = sensorValues[Sensor.TYPE_GYROSCOPE],
            magneticField = sensorValues[Sensor.TYPE_MAGNETIC_FIELD],
            availableSensors = names,
        )
    }
    private fun sensorName(type: Int) = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "accelerometer"
        Sensor.TYPE_GYROSCOPE -> "gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "magnetometer"
        Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "game_rotation_vector"
        Sensor.TYPE_GRAVITY -> "gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "linear_acceleration"
        else -> null
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun persist(m: LocalizationMeasurement) { runCatching { synchronized(store) { store.parentFile?.mkdirs(); if (!store.exists()) store.writeText("[]\n"); val text = store.readText().trim().removeSuffix("]").trim(); store.writeText((if (text == "[") "[" else "$text,\n") + JSONObject().put("peerId", m.peerId).put("timestamp", m.timestamp).put("transport", m.transport.name).put("source", m.source.name).put("bleRssiRaw", m.bleRssiRaw).put("bleRssiFiltered", m.bleRssiFiltered).put("ackRttMs", m.ackRttMs).toString(2) + "]\n") } } }
}
