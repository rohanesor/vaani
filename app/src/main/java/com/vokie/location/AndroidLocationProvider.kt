package com.vokie.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Foreground-only, bounded platform LocationManager adapter; it never fabricates a fix. */
class AndroidLocationProvider(
    private val context: Context,
    private val minIntervalMs: Long = 10_000L,
    private val minDistanceMeters: Float = 10f,
) : LocationProvider, LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _location = MutableStateFlow(initial())
    override val location: StateFlow<LocationMetadata> = _location
    private var sequence = 0L

    fun start() {
        if (!hasPermission()) { _location.value = LocationMetadata(availability = LocationAvailability.PERMISSION_REQUIRED); return }
        if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { _location.value = LocationMetadata(availability = LocationAvailability.UNAVAILABLE); return }
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minIntervalMs, minDistanceMeters, this) }
            .recoverCatching { manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minIntervalMs, minDistanceMeters, this) }
            .onFailure { _location.value = LocationMetadata(availability = LocationAvailability.ERROR) }
    }
    fun stop() { runCatching { manager?.removeUpdates(this) } }
    override fun onLocationChanged(location: Location) { _location.value = location.toMetadata(++sequence) }
    override fun onProviderDisabled(provider: String) { _location.value = LocationMetadata(availability = LocationAvailability.UNAVAILABLE) }
    override fun onProviderEnabled(provider: String) = Unit
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    private fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun initial() = if (hasPermission()) LocationMetadata(availability = LocationAvailability.UNAVAILABLE) else LocationMetadata(availability = LocationAvailability.PERMISSION_REQUIRED)
}

fun locationMetadataFromRaw(latitude: Double, longitude: Double, accuracyMeters: Float?, timestamp: Long, sequence: Long): LocationMetadata =
    if (!latitude.isFinite() || !longitude.isFinite() || accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters < 0f || timestamp <= 0L) LocationMetadata(availability = LocationAvailability.INVALID_FIX)
    else LocationMetadata(latitude, longitude, accuracyMeters, timestamp, sequence, LocationAvailability.AVAILABLE)

fun Location.toMetadata(sequence: Long): LocationMetadata = locationMetadataFromRaw(latitude, longitude, if (hasAccuracy()) accuracy else null, time, sequence)
