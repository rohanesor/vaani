package com.vokie.map

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mapDataStore: DataStore<Preferences> by preferencesDataStore(name = "vokie_map")

class MapPreferences(context: Context) {
    private val store = context.mapDataStore

    val activeRegionId: Flow<String> = store.data.map { it[ACTIVE_REGION] ?: DEFAULT_MAP_REGION_ID }
    val mapState: Flow<MapRegionState> = store.data.map {
        try {
            MapRegionState.valueOf(it[REGION_STATE] ?: MapRegionState.NOT_DOWNLOADED.name)
        } catch (_: IllegalArgumentException) {
            MapRegionState.NOT_DOWNLOADED
        }
    }

    suspend fun setActiveRegion(id: String) = store.edit { it[ACTIVE_REGION] = id }
    suspend fun setMapState(state: MapRegionState) = store.edit { it[REGION_STATE] = state.name }
    suspend fun setInstalledVersion(regionId: String, version: Int, timestamp: Long) = store.edit {
        it[versionKey(regionId)] = version
        it[updatedKey(regionId)] = timestamp
    }

    fun installedVersion(regionId: String): Flow<Int?> = store.data.map { it[versionKey(regionId)] }
    fun installedTimestamp(regionId: String): Flow<Long?> = store.data.map { it[updatedKey(regionId)] }

    private fun versionKey(id: String) = intPreferencesKey("version_$id")
    private fun updatedKey(id: String) = longPreferencesKey("updated_$id")

    companion object {
        private val ACTIVE_REGION = stringPreferencesKey("active_region")
        private val REGION_STATE = stringPreferencesKey("region_state")
    }
}
