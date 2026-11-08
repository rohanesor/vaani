package com.vokie.ranging

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import org.json.JSONObject

/** Device capability inventory only. No ranging, distance, direction, or GPS inference. */
class RangingCapabilityManager(private val context: Context) {
    private val packageManager = context.packageManager
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetooth = context.getSystemService(BluetoothAdapter::class.java)
    private val sensors = context.getSystemService(SensorManager::class.java)

    fun inspect(): RangingCapabilities {
        val wifiSupported = packageManager.hasSystemFeature("android.hardware.wifi")
        val rttHardware = packageManager.hasSystemFeature("android.hardware.wifi.rtt")
        val awareHardware = packageManager.hasSystemFeature("android.hardware.wifi.aware")
        val btSupported = packageManager.hasSystemFeature("android.hardware.bluetooth")
        val bleSupported = packageManager.hasSystemFeature("android.hardware.bluetooth_le")
        return RangingCapabilities(
            sdk = Build.VERSION.SDK_INT, manufacturer = Build.MANUFACTURER, model = Build.MODEL,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown", chipset = Build.HARDWARE,
            wifiSupported = wifiSupported, wifiEnabled = wifi?.isWifiEnabled == true,
            wifiRttHardware = rttHardware, wifiRttManager = context.getSystemService(WifiRttManager::class.java) != null,
            wifiAwareHardware = awareHardware, wifiAwareManager = context.getSystemService(WifiAwareManager::class.java) != null,
            wifiDirectSupported = packageManager.hasSystemFeature("android.hardware.wifi.direct"),
            bluetoothSupported = btSupported, bluetoothEnabled = bluetooth?.isEnabled == true,
            bleSupported = bleSupported,
            bleScanPermission = if (Build.VERSION.SDK_INT >= 31) context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED else btSupported,
            bleAdvertisePermission = if (Build.VERSION.SDK_INT >= 31) context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == android.content.pm.PackageManager.PERMISSION_GRANTED else btSupported,
            sensors = mapOf(
                "accelerometer" to hasSensor(Sensor.TYPE_ACCELEROMETER), "gyroscope" to hasSensor(Sensor.TYPE_GYROSCOPE),
                "magnetometer" to hasSensor(Sensor.TYPE_MAGNETIC_FIELD), "rotation_vector" to hasSensor(Sensor.TYPE_ROTATION_VECTOR),
                "game_rotation_vector" to hasSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), "gravity" to hasSensor(Sensor.TYPE_GRAVITY),
                "linear_acceleration" to hasSensor(Sensor.TYPE_LINEAR_ACCELERATION),
            ),
        ).also { Log.i(TAG, "CAPABILITIES ${it.toJson()}") }
    }
    private fun hasSensor(type: Int) = sensors?.getDefaultSensor(type) != null
    companion object { const val TAG = "VOKIE_RANGING" }
}

data class RangingCapabilities(
    val sdk: Int, val manufacturer: String, val model: String, val abi: String, val chipset: String,
    val wifiSupported: Boolean, val wifiEnabled: Boolean, val wifiRttHardware: Boolean, val wifiRttManager: Boolean,
    val wifiAwareHardware: Boolean, val wifiAwareManager: Boolean, val wifiDirectSupported: Boolean,
    val bluetoothSupported: Boolean, val bluetoothEnabled: Boolean, val bleSupported: Boolean,
    val bleScanPermission: Boolean, val bleAdvertisePermission: Boolean, val sensors: Map<String, Boolean>,
) {
    fun toJson() = JSONObject().apply {
        put("sdk", sdk); put("manufacturer", manufacturer); put("model", model); put("abi", abi); put("chipset", chipset)
        put("wifi_supported", wifiSupported); put("wifi_enabled", wifiEnabled); put("wifi_rtt_hardware", wifiRttHardware); put("wifi_rtt_manager", wifiRttManager)
        put("wifi_aware_hardware", wifiAwareHardware); put("wifi_aware_manager", wifiAwareManager); put("wifi_direct_supported", wifiDirectSupported)
        put("bluetooth_supported", bluetoothSupported); put("bluetooth_enabled", bluetoothEnabled); put("ble_supported", bleSupported)
        put("ble_scan_permission", bleScanPermission); put("ble_advertise_permission", bleAdvertisePermission); put("sensors", JSONObject(sensors))
    }.toString()
}

data class RangingMeasurement(
    val measurementId: String, val timestamp: Long, val localDeviceId: String, val peerDeviceId: String?, val transport: String?,
    val wifiRtt: Long? = null, val bleRssi: Int? = null, val ackRtt: Long? = null, val packetDeliveryTime: Long? = null,
    val retryCount: Int? = null, val wifiDirectState: String? = null, val bluetoothState: String? = null,
    val orientation: List<Float>? = null, val accelerometer: List<Float>? = null, val gyroscope: List<Float>? = null,
    val magnetometer: List<Float>? = null, val sourceCapability: String? = null, val measurementQuality: String? = null,
)
