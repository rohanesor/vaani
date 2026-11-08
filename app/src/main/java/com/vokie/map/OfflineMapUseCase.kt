package com.vokie.map

import android.content.Context
import android.net.Uri
import com.vokie.communication.VokieLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** UI-facing boundary for offline map region management. */
class OfflineMapUseCase(
    private val context: Context,
    private val manager: MapPackManager,
    private val preferences: MapPreferences,
) {
    private val _status = MutableStateFlow(MapStatus(DefaultMapRegion, MapRegionState.NOT_DOWNLOADED))
    val status: StateFlow<MapStatus> = _status.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val region = DefaultMapRegion
        val installed = manager.isInstalled(region)
        val size = if (installed) manager.installedSizeBytes(region) else 0L
        val version = preferences.installedVersion(region.id).first()
        val timestamp = preferences.installedTimestamp(region.id).first()
        val state = when {
            !installed -> MapRegionState.NOT_DOWNLOADED
            version != null && version < region.version -> MapRegionState.UPDATE_AVAILABLE
            else -> MapRegionState.READY
        }
        _status.value = MapStatus(region, state, size, version, lastUpdated = timestamp)
    }

    suspend fun downloadDefaultRegion() = withContext(Dispatchers.IO) {
        val region = DefaultMapRegion
        _status.value = _status.value.copy(state = MapRegionState.DOWNLOADING, failure = null)
        try {
            val metadata = manager.installBundledDefault()
            preferences.setActiveRegion(region.id)
            preferences.setMapState(MapRegionState.READY)
            preferences.setInstalledVersion(region.id, metadata.version, metadata.downloadedAt)
            _status.value = MapStatus(region, MapRegionState.READY, manager.installedSizeBytes(region), metadata.version, lastUpdated = metadata.downloadedAt)
            VokieLog.msg("Map region installed: ${region.name}")
        } catch (error: Throwable) {
            val failure = mapMapFailure(error)
            _status.value = _status.value.copy(state = MapRegionState.FAILED, failure = failure)
            throw error
        }
    }

    suspend fun importRegion(uri: Uri) = withContext(Dispatchers.IO) {
        val previous = _status.value
        _status.value = previous.copy(state = MapRegionState.DOWNLOADING, failure = null)
        try {
            val metadata = manager.importPack(uri)
            preferences.setActiveRegion(metadata.id)
            preferences.setMapState(MapRegionState.READY)
            preferences.setInstalledVersion(metadata.id, metadata.version, metadata.downloadedAt)
            _status.value = MapStatus(
                MapRegion(metadata.id, metadata.name, "", metadata.version, manager.installedSizeBytes(DefaultMapRegion)),
                MapRegionState.READY,
                manager.installedSizeBytes(DefaultMapRegion),
                metadata.version,
                lastUpdated = metadata.downloadedAt,
            )
            VokieLog.msg("Map region imported: ${metadata.name}")
        } catch (error: Throwable) {
            val failure = mapMapFailure(error)
            _status.value = previous.copy(state = MapRegionState.FAILED, failure = failure)
            throw error
        }
    }

    suspend fun loadActivePoints(): List<MapPoint> = withContext(Dispatchers.IO) {
        val activeId = preferences.activeRegionId.first()
        val region = if (activeId == DefaultMapRegion.id) DefaultMapRegion else DefaultMapRegion.copy(id = activeId)
        manager.loadPoints(region)
    }

    suspend fun deleteRegion() = withContext(Dispatchers.IO) {
        manager.delete(DefaultMapRegion)
        preferences.setMapState(MapRegionState.NOT_DOWNLOADED)
        refresh()
    }

    private fun mapMapFailure(error: Throwable): MapFailure = when (error) {
        is MapException -> MapFailure(error.code, error.message ?: "Map operation failed", error)
        is OutOfMemoryError -> MapFailure(MapErrorCode.STORAGE_FULL, "Not enough storage for the offline map region.", error)
        else -> MapFailure(MapErrorCode.DOWNLOAD_FAILED, error.message ?: "Map operation failed.", error)
    }
}
