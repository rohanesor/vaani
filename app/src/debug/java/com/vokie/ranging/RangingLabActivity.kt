package com.vokie.ranging

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.View
import android.widget.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import com.vokie.VokieApplication
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** DEBUG-only manual validation workflow. It never manufactures labels or measurements. */
class RangingLabActivity : Activity() {
    private val serviceUuid = UUID.fromString("0000f4b0-0000-1000-8000-00805f9b34fb")
    private lateinit var status: TextView
    private lateinit var experimentId: EditText
    private lateinit var runId: EditText
    private lateinit var distance: EditText
    private lateinit var angle: EditText
    private lateinit var type: Spinner
    private lateinit var recorder: PhysicalValidationRecorder
    private lateinit var app: VokieApplication
    private val scope = MainScope()
    private var lastRecordedTimestamp: Long? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); app = application as VokieApplication
        recorder = PhysicalValidationRecorder(File(filesDir, "model-lab/ranging/validation"))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 28) }
        fun label(text: String) = TextView(this).apply { this.text = text; textSize = 16f }
        root.addView(label("VOKIE VALIDATION MODE"))
        experimentId = EditText(this).apply { hint = "Experiment ID (required)" }; root.addView(experimentId)
        runId = EditText(this).apply { hint = "Run ID (required)" }; root.addView(runId)
        type = Spinner(this).also { it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ValidationExperimentType.entries.map { e -> e.name }) }; root.addView(type)
        distance = EditText(this).apply { hint = "Ground-truth distance (metres, optional)"; inputType = 8194 }; root.addView(distance)
        angle = EditText(this).apply { hint = "Ground-truth angle (0-359 degrees, optional)"; inputType = 2 }; root.addView(angle)
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { runCatching(action).onFailure { showError(it.message ?: "Validation error") } } }
        root.addView(button("Start Experiment") { recorder.startExperiment(experimentId.text.toString().trim(), ValidationExperimentType.valueOf(type.selectedItem as String)); lastRecordedTimestamp = null; refreshStatus() })
        root.addView(button("Start Run / Record Automatically") { recorder.startRun(runId.text.toString().trim()); refreshStatus() })
        root.addView(button("Record Sample Now") { recordLatest(true) })
        root.addView(button("Stop Run") { recorder.stopRun(); refreshStatus() })
        root.addView(button("Finalize Experiment") { recorder.finalizeExperiment(); refreshStatus() })
        root.addView(button("Reset") { recorder.resetExperiment(); lastRecordedTimestamp = null; refreshStatus() })
        root.addView(button("Start BLE Scanner") { startScan() })
        root.addView(button("Start BLE Advertiser") { startAdvertise() })
        root.addView(button("Stop BLE") { stopBle() })
        status = label(""); status.setPadding(0, 18, 0, 0); root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
        scope.launch { recorder.currentRunSampleCount.collect { refreshStatus() } }
        scope.launch { app.localizationEngine.states.collect { if (recorder.isRecording()) recordLatest(false); refreshStatus(it.values.maxByOrNull { s -> s.lastSeen }) } }
        refreshStatus()
    }

    private fun refreshStatus(state: LocalizationState? = app.localizationEngine.states.value.values.maxByOrNull { it.lastSeen }) {
        val typeName = recorder.currentType()?.name ?: "NOT STARTED"
        val motion = state?.lastMeasurement?.motionSnapshot
        status.text = buildString {
            appendLine("Experiment: ${recorder.currentExperiment() ?: "NONE"}")
            appendLine("Run: ${recorder.currentRun() ?: "NONE"}")
            appendLine("Type: $typeName")
            appendLine("Recording: ${if (recorder.isRecording()) "YES" else "NO"}")
            appendLine("Samples: ${recorder.currentRunSampleCount.value}")
            appendLine("Peer: ${state?.peerId ?: "UNKNOWN"}")
            appendLine("BLE RSSI: ${state?.rssi?.latest ?: "UNKNOWN"}")
            appendLine("Filtered RSSI: ${state?.filteredRssi ?: "UNKNOWN"}")
            appendLine("Motion: ${state?.lastMeasurement?.let { MotionClassifier().classify(listOf(it)) } ?: "UNKNOWN"}")
            appendLine("Distance: UNKNOWN (no validated calibration window)")
            appendLine("Distance confidence: LOW")
            appendLine("Direction: UNKNOWN")
            appendLine("Direction confidence: LOW")
            appendLine("Wi-Fi RTT: UNAVAILABLE unless capability and responder exist")
            appendLine("Sensors: ${motion?.availableSensors ?: "UNAVAILABLE"}")
            appendLine("GPS: NOT USED")
            appendLine("Files: files/model-lab/ranging/validation/")
        }
    }

    private fun recordLatest(force: Boolean) {
        val state = app.localizationEngine.states.value.values.maxByOrNull { it.lastSeen } ?: return
        val m = state.lastMeasurement ?: return
        if (!force && m.timestamp == lastRecordedTimestamp) return
        val selectedDistance = distance.text.toString().trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val selectedAngle = angle.text.toString().trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val snapshot = m.motionSnapshot
        recorder.recordSample(ValidationMeasurement(
            experimentId = recorder.currentExperiment()!!, runId = recorder.currentRun()!!, deviceId = app.deviceId, peerId = state.peerId, timestamp = m.timestamp,
            rawRssi = m.bleRssiRaw, filteredRssi = m.bleRssiFiltered, wifiDirectState = app.wifiDirectTransport.state.value.name,
            bluetoothState = app.bluetoothTransport.connectionState.value.name, connectionState = app.transportManager.connectionState.value.name,
            transport = m.transport.name, motionState = MotionClassifier().classify(listOf(m)).name, orientation = snapshot?.orientation,
            gyro = snapshot?.angularVelocity, accelerometer = snapshot?.acceleration, magnetometer = snapshot?.magneticField,
            rotationVector = snapshot?.orientation, groundTruthDistanceMeters = selectedDistance, groundTruthAngleDegrees = selectedAngle,
            estimatedDistanceMeters = null, estimatedBearingDegrees = null, distanceState = "UNKNOWN_RANGE", directionSector = "UNKNOWN",
            confidence = "LOW", uncertaintyMeters = null, uncertaintyDegrees = null, sampleCount = recorder.currentRunSampleCount.value + 1,
            experimentType = recorder.currentType()!!.name, measurementSource = if (m.source == LocalizationSource.BLE_RSSI) "BLE" else m.source.name,
            wifiRttAvailable = false, deviceModel = Build.MODEL, androidSdk = Build.VERSION.SDK_INT, localRole = "MEASUREMENT",
            ackRttMs = m.ackRttMs,
        ))
        lastRecordedTimestamp = m.timestamp; refreshStatus(state)
    }

    @SuppressLint("MissingPermission") private fun startScan() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: error("Bluetooth unavailable")
        scanner = adapter.bluetoothLeScanner ?: error("BLE scanner unavailable")
        scanCallback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val peer = result.device.address
                app.localizationEngine.recordRssi(peer, result.rssi)
                android.util.Log.i("VOKIE_BLE_LAB", "VALIDATION_RSSI peer=$peer rssi=${result.rssi}")
            }
            override fun onScanFailed(errorCode: Int) { android.util.Log.e("VOKIE_BLE_LAB", "VALIDATION_SCAN_FAILURE code=$errorCode") }
        }
        scanner!!.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        android.util.Log.i("VOKIE_VALIDATION", "BLE_SCAN_STARTED")
    }

    @SuppressLint("MissingPermission") private fun startAdvertise() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: error("Bluetooth unavailable")
        advertiser = adapter.bluetoothLeAdvertiser ?: error("BLE advertiser unavailable")
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(serviceUuid)).addServiceData(ParcelUuid(serviceUuid), byteArrayOf(1, 2, 3)).build()
        val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).setConnectable(false).build()
        advertiseCallback = object : AdvertiseCallback() { override fun onStartSuccess(s: AdvertiseSettings) { android.util.Log.i("VOKIE_VALIDATION", "BLE_ADVERTISE_STARTED") }; override fun onStartFailure(code: Int) { android.util.Log.e("VOKIE_VALIDATION", "BLE_ADVERTISE_FAILURE code=$code") } }
        advertiser!!.startAdvertising(settings, data, advertiseCallback)
    }
    @SuppressLint("MissingPermission") private fun stopBle() { scanCallback?.let { scanner?.stopScan(it) }; advertiseCallback?.let { advertiser?.stopAdvertising(it) }; scanCallback = null; advertiseCallback = null; android.util.Log.i("VOKIE_VALIDATION", "BLE_STOPPED") }
    private fun showError(message: String) { android.util.Log.e("VOKIE_VALIDATION", "VALIDATION_ERROR $message"); Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onDestroy() { stopBle(); if (recorder.isRecording()) recorder.stopRun(); scope.cancel(); super.onDestroy() }
}
