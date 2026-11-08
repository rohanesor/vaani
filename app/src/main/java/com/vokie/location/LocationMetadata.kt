package com.vokie.location

enum class LocationAvailability { AVAILABLE, UNAVAILABLE, PERMISSION_REQUIRED, PERMISSION_DENIED, STALE, INVALID_FIX, INVALID, ERROR }

/** Raw sender/receiver fix. AVAILABLE is the only state allowed to carry coordinates. */
data class LocationMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val timestamp: Long? = null,
    val locationSequence: Long = 0,
    val availability: LocationAvailability,
) {
    init {
        require(locationSequence >= 0)
        if (availability == LocationAvailability.AVAILABLE) {
            require(latitude != null && latitude.isFinite() && latitude in -90.0..90.0)
            require(longitude != null && longitude.isFinite() && longitude in -180.0..180.0)
            require(accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters >= 0f)
            require(timestamp != null && timestamp > 0)
        }
    }
}
