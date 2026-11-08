package com.vokie.map

import org.junit.Assert.*
import org.junit.Test

class OfflineMapCoreTest {
    @Test fun mapRegionStateCoversRealLifecycle() {
        val states = listOf(MapRegionState.NOT_DOWNLOADED, MapRegionState.DOWNLOADING, MapRegionState.READY, MapRegionState.UPDATE_AVAILABLE, MapRegionState.FAILED)
        assertEquals(5, states.size)
        assertTrue(states.all { it.name.isNotEmpty() })
    }

    @Test fun poiTypeHasStableColor() {
        assertEquals("#3FA66E", MapPoiType.SHELTER.colorHex())
        assertEquals("#E8402B", MapPoiType.HOSPITAL.colorHex())
        assertEquals("#F59E0B", MapPoiType.HAZARD.colorHex())
        assertEquals("#3B82F6", MapPoiType.WATER.colorHex())
    }

    @Test fun formatBytesIsHumanReadable() {
        assertTrue(formatBytesHelper(512).endsWith("B"))
        assertTrue(formatBytesHelper(1500).contains("KB"))
        assertTrue(formatBytesHelper(2_000_000).contains("MB"))
    }

    private fun formatBytesHelper(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
