package com.vokie.location

import com.vokie.communication.PacketTransportState
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

/** Qualitative, communication-triggered proximity; deliberately contains no coordinates or metres. */
enum class ProximityZone { UNKNOWN, NEAR, VERY_NEAR }
enum class MeasurementConfidence { LOW, MEDIUM, HIGH }
enum class MeasurementTrigger { MESSAGE_SENT, MESSAGE_RECEIVED, ACK_RECEIVED }

data class RelativeProximityMeasurement(
    val peerId: String,
    val timestamp: Long,
    val trigger: MeasurementTrigger,
    val connectionState: TransportConnectionState,
    val wifiDirectState: PacketTransportState,
    val transport: TransportType?,
    val rttMs: Long?,
    val retryCount: Int?,
    val delivered: Boolean?,
    val zone: ProximityZone,
    val confidence: MeasurementConfidence,
    val staleAfterMs: Long = 120_000L,
) {
    fun isStale(now: Long): Boolean = now - timestamp > staleAfterMs
}

/** One latest snapshot per peer. A direct link proves reachability, not physical distance. */
class LocationMeasurementCollector {
    private val _latest = MutableStateFlow<Map<String, RelativeProximityMeasurement>>(emptyMap())
    val latest: StateFlow<Map<String, RelativeProximityMeasurement>> = _latest.asStateFlow()

    fun record(
        peerId: String,
        trigger: MeasurementTrigger,
        connectionState: TransportConnectionState,
        wifiDirectState: PacketTransportState,
        transport: TransportType?,
        timestamp: Long = System.currentTimeMillis(),
        rttMs: Long? = null,
        retryCount: Int? = null,
        delivered: Boolean? = null,
    ) {
        if (peerId.isBlank()) return
        val direct = transport == TransportType.WIFI_DIRECT && wifiDirectState == PacketTransportState.CONNECTED
        val acknowledged = delivered == true
        val zone = if (direct && acknowledged) ProximityZone.NEAR else ProximityZone.UNKNOWN
        val confidence = when {
            direct && acknowledged && rttMs != null -> MeasurementConfidence.MEDIUM
            direct && acknowledged -> MeasurementConfidence.LOW
            else -> MeasurementConfidence.LOW
        }
        val value = RelativeProximityMeasurement(peerId, timestamp, trigger, connectionState, wifiDirectState, transport, rttMs, retryCount, delivered, zone, confidence)
        _latest.value = _latest.value + (peerId to value)
        Log.i("VOKIE_PROXIMITY", "MEASUREMENT trigger=$trigger peer=$peerId transport=$transport connection=$connectionState wifi=$wifiDirectState rttMs=$rttMs retry=$retryCount delivered=$delivered zone=$zone confidence=$confidence")
    }

    fun clearStale(now: Long = System.currentTimeMillis()) {
        _latest.value = _latest.value.filterValues { !it.isStale(now) }
    }
}
