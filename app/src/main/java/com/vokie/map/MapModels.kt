package com.vokie.map

import android.content.Context
import java.io.File

const val DEFAULT_MAP_REGION_ID = "india-emergency-baseline"

enum class MapRegionState { NOT_DOWNLOADED, DOWNLOADING, READY, UPDATE_AVAILABLE, FAILED }

enum class MapPoiType { SHELTER, HOSPITAL, HAZARD, WATER, UNKNOWN }

data class MapPoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: MapPoiType,
    val details: String = "",
)

data class MapRegion(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val approximateSizeBytes: Long,
    val dataFileName: String = "region.geojson",
    val metadataFileName: String = "region.json",
) {
    companion object
}

data class MapRegionMetadata(
    val id: String,
    val name: String,
    val version: Int,
    val points: List<MapPoint>,
    val downloadedAt: Long,
)

data class MapFailure(val code: MapErrorCode, val userMessage: String, val cause: Throwable? = null)

enum class MapErrorCode {
    REGION_INVALID,
    REGION_MISSING,
    STORAGE_FULL,
    DOWNLOAD_FAILED,
    PARSE_FAILED,
}

class MapException(val code: MapErrorCode, message: String, cause: Throwable? = null) : Exception(message, cause)

data class MapStatus(
    val region: MapRegion,
    val state: MapRegionState,
    val installedBytes: Long = 0,
    val installedVersion: Int? = null,
    val failure: MapFailure? = null,
    val lastUpdated: Long? = null,
)

val DefaultMapRegion = MapRegion(
    id = DEFAULT_MAP_REGION_ID,
    name = "India Emergency Baseline",
    description = "Sample shelters, hospitals, and hazard markers for offline demonstration. Import a full region pack to replace it.",
    version = 1,
    approximateSizeBytes = 16_000,
    dataFileName = "region.geojson",
    metadataFileName = "region.json",
)

fun MapRegion.regionDirectory(context: Context): File = File(File(context.filesDir, "map-regions"), id)
fun MapRegion.dataFile(context: Context): File = File(regionDirectory(context), dataFileName)
fun MapRegion.metadataFile(context: Context): File = File(regionDirectory(context), metadataFileName)

fun MapPoiType.colorHex(): String = when (this) {
    MapPoiType.SHELTER -> "#3FA66E"
    MapPoiType.HOSPITAL -> "#E8402B"
    MapPoiType.HAZARD -> "#F59E0B"
    MapPoiType.WATER -> "#3B82F6"
    MapPoiType.UNKNOWN -> "#9CA3AF"
}
