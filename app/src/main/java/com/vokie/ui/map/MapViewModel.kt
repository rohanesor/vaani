package com.vokie.ui.map

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vokie.VokieApplication
import com.vokie.map.MapPoint
import com.vokie.map.MapRegionState
import com.vokie.map.MapStatus
import com.vokie.map.OfflineMapUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val useCase: OfflineMapUseCase = (application as VokieApplication).offlineMap
    val status: StateFlow<MapStatus> = useCase.status
    private val _points = MutableStateFlow<List<MapPoint>>(emptyList())
    val points: StateFlow<List<MapPoint>> = _points.asStateFlow()

    init {
        viewModelScope.launch {
            useCase.status.collect { if (it.state == MapRegionState.READY) refreshPoints() }
        }
        viewModelScope.launch { useCase.refresh() }
    }

    fun downloadDefault() = viewModelScope.launch { runCatching { useCase.downloadDefaultRegion(); refreshPoints() } }
    fun importRegion(uri: Uri) = viewModelScope.launch { runCatching { useCase.importRegion(uri); refreshPoints() } }
    fun deleteRegion() = viewModelScope.launch { runCatching { useCase.deleteRegion(); refreshPoints() } }

    private suspend fun refreshPoints() { _points.value = useCase.loadActivePoints() }
}
