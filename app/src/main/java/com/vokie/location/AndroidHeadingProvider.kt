package com.vokie.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class HeadingStatus { AVAILABLE, UNAVAILABLE, SENSOR_UNAVAILABLE, LOW_CONFIDENCE }
data class HeadingReading(val headingDegrees: Double?, val accuracy: Int?, val timestamp: Long?, val status: HeadingStatus)

/** Rotation-vector heading adapter. It is not a claim of compass accuracy or sensor fusion. */
class AndroidHeadingProvider(context: Context) : HeadingProvider, SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val _heading = MutableStateFlow(Heading(null, null, HeadingSource.UNAVAILABLE))
    override val heading: StateFlow<Heading> = _heading
    val reading = MutableStateFlow(if (sensor == null) HeadingReading(null, null, null, HeadingStatus.SENSOR_UNAVAILABLE) else HeadingReading(null, null, null, HeadingStatus.UNAVAILABLE))
    private val filter = CircularHeadingFilter()
    fun start() { if (sensor == null) return; sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) }
    fun stop() { sensors?.unregisterListener(this) }
    override fun onSensorChanged(event: SensorEvent) {
        val rotation = FloatArray(9); SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val orientation = FloatArray(3); SensorManager.getOrientation(rotation, orientation)
        val degrees = filter.add(Math.toDegrees(orientation[0].toDouble()))
        val status = if (event.accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) HeadingStatus.LOW_CONFIDENCE else HeadingStatus.AVAILABLE
        reading.value = HeadingReading(degrees, event.accuracy, System.currentTimeMillis(), status)
        _heading.value = Heading(degrees, if (status == HeadingStatus.AVAILABLE) 1f else 0f, HeadingSource.ROTATION_VECTOR)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

class CircularHeadingFilter(private val alpha: Double = .25) {
    private var sinValue: Double? = null; private var cosValue: Double? = null
    fun add(degrees: Double): Double {
        val radians = Math.toRadians(((degrees % 360) + 360) % 360)
        sinValue = sinValue?.let { it * (1 - alpha) + kotlin.math.sin(radians) * alpha } ?: kotlin.math.sin(radians)
        cosValue = cosValue?.let { it * (1 - alpha) + kotlin.math.cos(radians) * alpha } ?: kotlin.math.cos(radians)
        return ((Math.toDegrees(kotlin.math.atan2(requireNotNull(sinValue), requireNotNull(cosValue))) + 360) % 360)
    }
}
