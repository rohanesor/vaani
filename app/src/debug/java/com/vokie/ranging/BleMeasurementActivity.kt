package com.vokie.ranging

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/** Debug-only BLE raw RSSI lab. It produces no distance or direction estimate. */
class BleMeasurementActivity : Activity() {
    private val serviceUuid = UUID.fromString("0000f4b0-0000-1000-8000-00805f9b34fb")
    private lateinit var view: TextView
    private val samples = mutableListOf<Int>()
    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null
    private var startedAt = 0L
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); view=TextView(this).apply { textSize=17f; setPadding(28,40,28,40); text="BLE MEASUREMENT LAB" }; setContentView(view)
        val role=intent.getStringExtra("role") ?: "SCAN"; val adapter=(getSystemService(BluetoothManager::class.java)).adapter
        check(adapter != null && adapter.isEnabled) { "Bluetooth is unavailable or disabled" }
        if (role == "ADVERTISE") startAdvertise(adapter) else startScan(adapter)
    }
    @Suppress("MissingPermission") private fun startAdvertise(adapter: BluetoothAdapter) {
        advertiser=adapter.bluetoothLeAdvertiser ?: error("BLE advertiser unavailable")
        val data=AdvertiseData.Builder().addServiceUuid(ParcelUuid(serviceUuid)).addServiceData(ParcelUuid(serviceUuid), byteArrayOf(1,2,3)).setIncludeDeviceName(false).build()
        val settings=AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).setConnectable(false).build()
        advertiser!!.startAdvertising(settings,data,object:AdvertiseCallback(){ override fun onStartSuccess(settingsInEffect:AdvertiseSettings){ log("ADVERTISE_STARTED uuid=$serviceUuid") }; override fun onStartFailure(errorCode:Int){ log("FAILURE advertise=$errorCode") } })
    }
    @Suppress("MissingPermission") private fun startScan(adapter: BluetoothAdapter) {
        scanner=adapter.bluetoothLeScanner ?: error("BLE scanner unavailable"); startedAt=System.currentTimeMillis()
        val filter=ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
        scanCallback=object:ScanCallback(){ override fun onScanResult(type:Int,result:ScanResult){ if(samples.size<30){ samples+=result.rssi; log("SAMPLE ${samples.size}/30 rssi=${result.rssi} address=${result.device.address}"); if(samples.size==30) finishSamples() } }; override fun onScanFailed(errorCode:Int){ log("FAILURE scan=$errorCode") } }
        scanner!!.startScan(listOf(filter),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
        log("SCAN_STARTED uuid=$serviceUuid")
    }
    @Suppress("MissingPermission") private fun finishSamples(){ scanCallback?.let { scanner?.stopScan(it) }; scanCallback=null; val ordered=samples.sorted(); val mean=samples.average(); val sd=sqrt(samples.map{(it-mean)*(it-mean)}.average()); val o=JSONObject().apply{put("timestamp",System.currentTimeMillis());put("serviceUuid",serviceUuid.toString());put("sampleCount",samples.size);put("minRssi",ordered.first());put("maxRssi",ordered.last());put("meanRssi",mean);put("medianRssi",ordered[ordered.size/2]);put("stddevRssi",sd)}; File(filesDir,"ranging-lab").apply{mkdirs()}.resolve("ble_measurements.json").writeText(o.toString()); log("COMPLETE samples=${samples.size} min=${ordered.first()} max=${ordered.last()} mean=$mean median=${ordered[ordered.size/2]} stddev=$sd") }
    private fun log(s:String){Log.i(TAG,s);runOnUiThread{view.append("\n$s")}}
    @Suppress("MissingPermission") override fun onDestroy(){ scanCallback?.let { scanner?.stopScan(it) }; advertiser?.stopAdvertising(advertiseCallback); super.onDestroy() }
    private val advertiseCallback=object:AdvertiseCallback(){}
    companion object{const val TAG="VOKIE_BLE_LAB"}
}
