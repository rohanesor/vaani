package com.vokie.proximity

import com.vokie.domain.model.Peer
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RssiAvailability { AVAILABLE, UNAVAILABLE, STALE, ERROR }
data class RssiTelemetryState(val filtered: FilteredRssi?, val availability: RssiAvailability)

/**
 * Android classic Bluetooth exposes RSSI on discovery broadcasts in the existing transport.
 * It exposes no standard connected-RFCOMM RSSI API, so this adapter only accepts those observed
 * discovery values and never polls or modifies transport framing.
 */
class BluetoothRssiTelemetryProvider(private val filter: RssiFilter = RssiFilter()) {
    private val _state = MutableStateFlow(RssiTelemetryState(null, RssiAvailability.UNAVAILABLE))
    val state: StateFlow<RssiTelemetryState> = _state
    fun recordDiscovery(peer: Peer, timestamp: Long): RssiTelemetryState {
        val rssi = peer.rssi ?: return _state.value
        val filtered = filter.add(ProximityTelemetry(peer.id, TransportType.BLUETOOTH, rssi, timestamp))
        return RssiTelemetryState(filtered, RssiAvailability.AVAILABLE).also { _state.value = it }
    }
    fun refresh(now: Long): RssiTelemetryState {
        val current = filter.current(now)
        val availability = when (current.freshness) { RssiFreshness.CURRENT -> RssiAvailability.AVAILABLE; RssiFreshness.STALE -> RssiAvailability.STALE; RssiFreshness.UNAVAILABLE -> RssiAvailability.UNAVAILABLE }
        return RssiTelemetryState(current, availability).also { _state.value = it }
    }
}
