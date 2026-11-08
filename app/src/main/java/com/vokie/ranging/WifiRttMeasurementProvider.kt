package com.vokie.ranging

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import java.util.concurrent.Executor

/** Optional RTT adapter. Unsupported hardware and permission failures are explicit, never estimates. */
data class WifiRttMeasurement(val timestamp: Long, val distanceMeters: Double?, val available: Boolean, val reason: String? = null)

class WifiRttMeasurementProvider(context: Context) {
    private val appContext = context.applicationContext
    private val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) appContext.getSystemService(WifiRttManager::class.java) else null
    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && manager != null && contextFeature(appContext)

    fun measure(responder: ScanResult?, executor: Executor, callback: (WifiRttMeasurement) -> Unit) {
        if (!isAvailable) { callback(WifiRttMeasurement(System.currentTimeMillis(), null, false, "Wi-Fi RTT unavailable")); return }
        if (responder == null) { callback(WifiRttMeasurement(System.currentTimeMillis(), null, false, "No RTT responder")); return }
        try {
            val request = RangingRequest.Builder().addAccessPoint(responder).build()
            manager!!.startRanging(request, executor, object : RangingResultCallback() {
                override fun onRangingResults(results: MutableList<RangingResult>) {
                    val result = results.firstOrNull { it.status == RangingResult.STATUS_SUCCESS }
                    callback(WifiRttMeasurement(System.currentTimeMillis(), result?.distanceMm?.takeIf { it > 0 }?.div(1000.0), result != null, if (result == null) "No successful RTT result" else null))
                }
                override fun onRangingFailure(code: Int) { callback(WifiRttMeasurement(System.currentTimeMillis(), null, false, "RTT failure: $code")) }
            })
        } catch (_: SecurityException) { callback(WifiRttMeasurement(System.currentTimeMillis(), null, false, "RTT permission unavailable")) }
        catch (_: RuntimeException) { callback(WifiRttMeasurement(System.currentTimeMillis(), null, false, "RTT request unavailable")) }
    }
    private fun contextFeature(context: Context) = context.packageManager.hasSystemFeature("android.hardware.wifi.rtt")
}
