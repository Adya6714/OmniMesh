package omnimesh.command1.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Estimates floor level using the device's barometric pressure sensor.
 *
 * Physics: Atmospheric pressure decreases with altitude at approximately
 * 1 hPa per 8.5 meters at sea level. Between standard building floors
 * (approximately 3.5 meters / floor), the pressure difference is roughly
 * 0.41 hPa per floor.
 *
 * Approach:
 * 1. Calibrate at ground level when outdoors (known pressure = floor 0)
 * 2. Track relative pressure change as user moves between floors
 * 3. Report floor estimate with confidence based on pressure stability
 */
class BarometricFloorEstimator(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "BarometricFloor"
        private const val FLOOR_HEIGHT_METERS = 3.5  // standard floor height
        private const val HPA_PER_METER = 0.12       // pressure change per meter
        private const val CALIBRATION_SAMPLES = 10  // samples to average for calibration
        private const val STABILITY_THRESHOLD_HPA = 0.05 // hPa — below this = stable reading
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var calibrationPressure: Float? = null  // pressure at floor 0
    private val calibrationBuffer = mutableListOf<Float>()
    private var currentPressure: Float? = null
    private val pressureBuffer = ArrayDeque<Float>(5) // rolling average

    private val _floorEstimate = MutableStateFlow<FloorEstimate?>(null)
    val floorEstimate: StateFlow<FloorEstimate?> = _floorEstimate.asStateFlow()

    val isAvailable: Boolean get() = pressureSensor != null

    fun start() {
        if (pressureSensor == null) {
            Log.w(TAG, "No barometric pressure sensor available on this device")
            return
        }
        sensorManager.registerListener(
            this,
            pressureSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        Log.d(TAG, "Barometric floor estimator started")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Calibrate at current location as floor 0 (ground level).
     * Call when device is confirmed to be at ground floor outdoors.
     */
    fun calibrateAsGroundFloor() {
        val currentPressure = this.currentPressure
        if (currentPressure != null) {
            calibrationPressure = currentPressure
            Log.d(TAG, "Calibrated at ${currentPressure} hPa = floor 0")
        } else {
            // Start collecting calibration samples
            calibrationBuffer.clear()
            Log.d(TAG, "Collecting calibration samples...")
        }
    }

    /**
     * Set calibration from an outdoor GPS fix.
     * Uses ICAO standard atmosphere to convert altitude to expected pressure.
     */
    fun calibrateFromAltitude(altitudeMeters: Float) {
        // Standard atmosphere: P = 1013.25 * (1 - 2.25577e-5 * h)^5.25588
        val alt = altitudeMeters.toDouble()
        val standardPressure =
            (1013.25 * (1.0 - 2.25577e-5 * alt).pow(5.25588)).toFloat()
        calibrationPressure = standardPressure
        Log.d(TAG, "Calibrated from altitude ${altitudeMeters}m: pressure=${standardPressure} hPa")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val rawPressure = event.values[0]

        // Rolling average to smooth noisy readings
        if (pressureBuffer.size >= 5) pressureBuffer.removeFirst()
        pressureBuffer.addLast(rawPressure)
        val smoothedPressure = pressureBuffer.map { it.toDouble() }.average().toFloat()
        currentPressure = smoothedPressure

        // Handle calibration collection
        if (calibrationPressure == null && calibrationBuffer.size < CALIBRATION_SAMPLES) {
            calibrationBuffer.add(smoothedPressure)
            if (calibrationBuffer.size == CALIBRATION_SAMPLES) {
                calibrationPressure = calibrationBuffer.map { it.toDouble() }.average().toFloat()
                Log.d(TAG, "Calibration complete: ${calibrationPressure} hPa = floor 0")
            }
            return
        }

        val baseline = calibrationPressure ?: return

        // Calculate floor from pressure difference
        val pressureDelta = baseline - smoothedPressure // positive = higher floor
        val altitudeDelta = pressureDelta / HPA_PER_METER
        val rawFloor = (altitudeDelta / FLOOR_HEIGHT_METERS).roundToInt()
        val clampedFloor = rawFloor.coerceIn(-2, 50) // basement to 50th floor

        // Confidence based on reading stability
        val recentVariance = if (pressureBuffer.size >= 3) {
            val mean = pressureBuffer.map { it.toDouble() }.average()
            pressureBuffer.map { d ->
                val x = d.toDouble() - mean
                x * x
            }.average().toFloat()
        } else {
            1.0f
        }

        val confidence = when {
            recentVariance < STABILITY_THRESHOLD_HPA -> 0.9f
            recentVariance < 0.2f -> 0.7f
            recentVariance < 0.5f -> 0.5f
            else -> 0.3f
        }

        _floorEstimate.value = FloorEstimate(
            floor = clampedFloor,
            confidence = confidence,
            pressureHpa = smoothedPressure,
            deltaFromBaselineHpa = pressureDelta,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}

data class FloorEstimate(
    val floor: Int,         // 0 = ground, negative = basement, positive = upper floor
    val confidence: Float,  // 0.0-1.0
    val pressureHpa: Float,
    val deltaFromBaselineHpa: Float,
)
