package com.vokie.map

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Manages offline map region packs in private app storage. No network I/O. */
class MapPackManager(private val context: Context) {
    private val _installedRegions = MutableStateFlow<Map<String, MapRegionMetadata>>(emptyMap())
    val installedRegions: StateFlow<Map<String, MapRegionMetadata>> = _installedRegions.asStateFlow()

    init { _installedRegions.value = scanInstalled() }

    fun isInstalled(region: MapRegion): Boolean = region.metadataFile(context).isFile && region.dataFile(context).isFile

    fun installedSizeBytes(region: MapRegion): Long = region.regionDirectory(context).walkBottomUp().filter { it.isFile }.map { it.length() }.sum()

    suspend fun installBundledDefault(): MapRegionMetadata = withContext(Dispatchers.IO) {
        val region = DefaultMapRegion
        val dir = region.regionDirectory(context)
        dir.mkdirs()
        context.assets.open("map/default-region.geojson").use { input ->
            FileOutputStream(region.dataFile(context)).use { output -> input.copyTo(output) }
        }
        val metadata = MapRegionMetadata(
            region.id, region.name, region.version,
            points = parseGeoJsonPoints(region.dataFile(context).readText()),
            downloadedAt = System.currentTimeMillis(),
        )
        writeMetadata(region, metadata)
        _installedRegions.value = scanInstalled()
        metadata
    }

    suspend fun importPack(uri: Uri): MapRegionMetadata = withContext(Dispatchers.IO) {
        val temp = File(File(context.filesDir, "map-regions"), ".importing")
        temp.deleteRecursively(); temp.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    val entries = mutableMapOf<String, File>()
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val name = entry.name.substringAfterLast('/')
                        val out = File(temp, name)
                        FileOutputStream(out).use { output -> zip.copyTo(output) }
                        entries[name] = out
                    }
                    val metadataFile = entries["region.json"] ?: throw MapException(MapErrorCode.REGION_INVALID, "ZIP must contain region.json")
                    val geojsonFile = entries["region.geojson"] ?: throw MapException(MapErrorCode.REGION_INVALID, "ZIP must contain region.geojson")
                    val metadataJson = JSONObject(metadataFile.readText())
                    val region = MapRegion(
                        id = metadataJson.getString("id"),
                        name = metadataJson.getString("name"),
                        description = metadataJson.optString("description"),
                        version = metadataJson.getInt("version"),
                        approximateSizeBytes = geojsonFile.length(),
                    )
                    val dir = region.regionDirectory(context)
                    dir.deleteRecursively(); dir.mkdirs()
                    check(geojsonFile.renameTo(region.dataFile(context))) { "Could not move region.geojson" }
                    check(metadataFile.renameTo(region.metadataFile(context))) { "Could not move region.json" }
                    val points = parseGeoJsonPoints(region.dataFile(context).readText())
                    val installed = MapRegionMetadata(region.id, region.name, region.version, points, System.currentTimeMillis())
                    writeMetadata(region, installed)
                    _installedRegions.value = scanInstalled()
                    installed
                }
            } ?: throw MapException(MapErrorCode.REGION_INVALID, "Selected map pack could not be opened")
        } catch (error: Throwable) {
            temp.deleteRecursively()
            if (error is MapException) throw error
            throw MapException(MapErrorCode.REGION_INVALID, error.message ?: "Invalid map pack", error)
        }
    }

    fun delete(region: MapRegion) {
        region.regionDirectory(context).deleteRecursively()
        _installedRegions.value = scanInstalled()
    }

    fun loadPoints(region: MapRegion): List<MapPoint> {
        val file = region.dataFile(context)
        if (!file.isFile) return emptyList()
        return runCatching { parseGeoJsonPoints(file.readText()) }.getOrDefault(emptyList())
    }

    private fun scanInstalled(): Map<String, MapRegionMetadata> {
        val root = File(context.filesDir, "map-regions")
        if (!root.isDirectory) return emptyMap()
        return root.listFiles { it -> it.isDirectory }?.mapNotNull { dir ->
            val metadataFile = File(dir, "region.json")
            if (!metadataFile.isFile) return@mapNotNull null
            runCatching { readMetadata(metadataFile) }.getOrNull()?.let { it.id to it }
        }?.toMap() ?: emptyMap()
    }

    private fun writeMetadata(region: MapRegion, metadata: MapRegionMetadata) {
        val json = JSONObject().apply {
            put("id", metadata.id)
            put("name", metadata.name)
            put("version", metadata.version)
            put("downloadedAt", metadata.downloadedAt)
            put("sha256", sha256(region.dataFile(context)))
        }
        region.metadataFile(context).writeText(json.toString(2))
    }

    private fun readMetadata(file: File): MapRegionMetadata {
        val json = JSONObject(file.readText())
        val dataFile = File(file.parentFile, "region.geojson")
        return MapRegionMetadata(
            id = json.getString("id"),
            name = json.getString("name"),
            version = json.getInt("version"),
            points = if (dataFile.isFile) runCatching { parseGeoJsonPoints(dataFile.readText()) }.getOrDefault(emptyList()) else emptyList(),
            downloadedAt = json.optLong("downloadedAt", System.currentTimeMillis()),
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun parseGeoJsonPoints(text: String): List<MapPoint> {
        val root = JSONObject(text)
        val features = root.getJSONArray("features")
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val geometry = feature.optJSONObject("geometry")
            val coords = geometry?.optJSONArray("coordinates") ?: return@mapNotNull null
            val props = feature.optJSONObject("properties") ?: JSONObject()
            val type = when (props.optString("type", "").lowercase()) {
                "shelter" -> MapPoiType.SHELTER
                "hospital" -> MapPoiType.HOSPITAL
                "hazard" -> MapPoiType.HAZARD
                "water" -> MapPoiType.WATER
                else -> MapPoiType.UNKNOWN
            }
            MapPoint(
                id = props.optString("id", "poi-$i"),
                name = props.optString("name", "Unnamed"),
                lat = coords.getDouble(1),
                lon = coords.getDouble(0),
                type = type,
                details = props.optString("details", ""),
            )
        }
    }
}
