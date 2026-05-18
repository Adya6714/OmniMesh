package omnimesh.command1.service

import android.app.*
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.omnimesh.app.ml.TriageFusionPipeline
import com.omnimesh.app.ml.VisionClassifier
import omnimesh.command1.LastCaptureImagePathStore
import omnimesh.command1.OmniMeshApp
import omnimesh.command1.command.TimelineEventType
import omnimesh.command1.ml.MotionClass
import omnimesh.command1.ml.MotionSignal
import omnimesh.command1.ml.MotionStateClassifier
import omnimesh.command1.ml.SensorRingBuffer
import kotlinx.coroutines.*

private const val TAG = "CollapseDetector"
private const val SOS_DEBUG_TAG = "SOS_DEBUG"
private const val CHANNEL_ID = "omnimesh_collapse_channel"
private const val NOTIFICATION_ID = 1002

// 💡 50Hz = 50 sensor readings per second.
// This matches our training data sample rate — must be the same
// or the LSTM will misclassify (it learned patterns at 50Hz).
private const val SENSOR_RATE_HZ = 50
private const val SENSOR_DELAY_US = 1_000_000 / SENSOR_RATE_HZ  // microseconds

// How confident must the model be before auto-triggering SOS?
// 0.70 = 70% — lowered from 0.85 to improve detection in real-world drop tests
private const val COLLAPSE_CONFIDENCE_THRESHOLD = 0.70f

// G-force threshold for the raw impact detector (bypasses LSTM)
// 3.5G avoids false alarms from placing phone down hard (~2G) while catching real drops (~4-8G)
private const val IMPACT_G_THRESHOLD = 3.5f
private const val IMPACT_STILLNESS_WINDOW_MS = 5_000L

// After auto-SOS, wait this long before allowing another
// 💡 Prevents rapid-fire duplicate packets if person is still moving after collapse
private const val COOLDOWN_MS = 30_000L  // 30 seconds

class CollapseDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var classifier: MotionStateClassifier
    private lateinit var visionClassifier: VisionClassifier
    private lateinit var fusionPipeline: TriageFusionPipeline
    private val sensorBuffer = SensorRingBuffer()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var lastLocation: Location? = null
    private var lastCollapseTs = 0L
    private var gyroBuffer = FloatArray(3)  // latest gyro reading

    private var pendingBeaconJob: Job? = null
    private var lastImpactTs = 0L
    private var postImpactStillCount = 0

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        classifier = MotionStateClassifier(this)
        visionClassifier = VisionClassifier(this)
        fusionPipeline = TriageFusionPipeline(visionClassifier)

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Monitoring for collapse events"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Monitoring for collapse events")
            )
        }
        registerSensors()
        Log.d(TAG, "Collapse detector started")
        return START_STICKY
    }

    // ─────────────────────────────────────────────
    // SENSOR REGISTRATION
    // ─────────────────────────────────────────────

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY_US)
            Log.d(TAG, "Accelerometer registered at ${SENSOR_RATE_HZ}Hz")
        } ?: Log.e(TAG, "No accelerometer found on this device")

        gyroscope?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY_US)
        } ?: Log.w(TAG, "No gyroscope — using zero gyro values")
    }

    // ─────────────────────────────────────────────
    // SENSOR DATA → RING BUFFER → LSTM INFERENCE
    // ─────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // 💡 Divide by 9.81 to convert m/s² → G-forces.
                // Our model was trained on G-force values, not raw m/s².
                val ax = event.values[0] / 9.81f
                val ay = event.values[1] / 9.81f
                val az = event.values[2] / 9.81f
                sensorBuffer.push(ax, ay, az, gyroBuffer[0], gyroBuffer[1], gyroBuffer[2])
                onNewSample(ax, ay, az)
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroBuffer = event.values.copyOf()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onNewSample(ax: Float, ay: Float, az: Float) {
        val magnitude = Math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val now = System.currentTimeMillis()

        // --- RAW IMPACT DETECTOR ---
        // Detects high-G impact followed by stillness (phone not moving = person unconscious)
        if (now - lastCollapseTs >= COOLDOWN_MS) {
            if (magnitude > IMPACT_G_THRESHOLD) {
                if (lastImpactTs == 0L || now - lastImpactTs > 10_000L) {
                    lastImpactTs = now
                    postImpactStillCount = 0
                    Log.d(TAG, "Impact detected: ${magnitude}G — watching for stillness")
                }
            }

            if (lastImpactTs > 0 && now - lastImpactTs in 1_000L..IMPACT_STILLNESS_WINDOW_MS) {
                val isStill = magnitude in 0.8f..1.3f
                if (isStill) postImpactStillCount++ else postImpactStillCount = 0

                // ~2 seconds of stillness after impact (100 samples at 50Hz)
                if (postImpactStillCount >= 100) {
                    Log.w(TAG, "Impact + stillness confirmed — triggering auto-SOS")
                    lastImpactTs = 0L
                    postImpactStillCount = 0
                    val impactSignal = MotionSignal(
                        motionClass = MotionClass.COLLAPSE_UNCONSCIOUS,
                        confidence = 0.78f,
                        collapseUnconscious = 0.78f,
                        collapseMoving = 0.1f,
                        allProbabilities = floatArrayOf(0.78f, 0.1f, 0.05f, 0.03f, 0.02f, 0.02f)
                    )
                    scope.launch { handleCollapseDetected(impactSignal) }
                    return
                }
            }

            if (lastImpactTs > 0 && now - lastImpactTs > IMPACT_STILLNESS_WINDOW_MS) {
                lastImpactTs = 0L
                postImpactStillCount = 0
            }
        }

        // --- LSTM DETECTOR ---
        if (!sensorBuffer.isFull()) return
        if (!sensorBuffer.hasRecentSpike(threshold = 2.5f)) return
        if (now - lastCollapseTs < COOLDOWN_MS) return

        scope.launch {
            val signal = classifier.classify(sensorBuffer.toFlatArray())
            Log.v(TAG, "Motion: ${signal.motionClass.label} @ ${signal.confidence}")
            val app = application as OmniMeshApp
            val pseudoAudioProbabilities = floatArrayOf(
                0.1f,
                0.1f,
                signal.confidence.coerceIn(0f, 1f),
                0.1f,
                0.1f,
                0.1f
            )
            app.structuralDangerDetector.currentLat = lastLocation?.latitude ?: 0.0
            app.structuralDangerDetector.currentLon = lastLocation?.longitude ?: 0.0
            app.structuralDangerDetector.onAudioInference(pseudoAudioProbabilities)

            if ((signal.motionClass == MotionClass.COLLAPSE_UNCONSCIOUS ||
                 signal.motionClass == MotionClass.COLLAPSE_MOVING ||
                 signal.motionClass == MotionClass.CAR_CRASH) &&
                signal.confidence >= COLLAPSE_CONFIDENCE_THRESHOLD) {
                handleCollapseDetected(signal)
            }
        }
    }

    // ─────────────────────────────────────────────
    // COLLAPSE CONFIRMED — auto-generate SOS packet
    // ─────────────────────────────────────────────

    private suspend fun handleCollapseDetected(motion: MotionSignal) {
        lastCollapseTs = System.currentTimeMillis()
        Log.w(TAG, "⚠️ COLLAPSE DETECTED — confidence: ${motion.confidence}")

        val location = lastLocation
        val rawLat = location?.latitude ?: 0.0
        val rawLon = location?.longitude ?: 0.0

        val app = application as OmniMeshApp
        val bestLoc = app.indoorLocationManager.getCurrentBestLocation()
        val packetLat = bestLoc?.lat ?: rawLat
        val packetLon = bestLoc?.lon ?: rawLon
        val floorStr = bestLoc?.estimatedFloor?.let { "Floor $it" } ?: ""
        val methodStr = bestLoc?.method?.name ?: "GPS"
        val accuracyStr = bestLoc?.let { "±${it.accuracyMeters.toInt()}m" } ?: ""
        val locString = buildString {
            append("Auto-detected collapse.")
            if (floorStr.isNotEmpty()) append(" ").append(floorStr)
            if (accuracyStr.isNotEmpty()) append(" ").append(accuracyStr)
            append(" [").append(methodStr).append("]")
        }

        val currentImagePath = LastCaptureImagePathStore.get()
        val basePacket = fusionPipeline.buildCollapseAutoSosPacket(
            motion = motion,
            currentImagePath = currentImagePath,
            latitude = packetLat,
            longitude = packetLon,
            context = this
        )
        val packet = basePacket.copy(loc = locString)

        val repository = app.repository
        repository.save(packet)
        Log.d(
            SOS_DEBUG_TAG,
            "Auto SOS packet created id=${packet.id} urgency=${packet.urgency} lat=${packet.lat} lon=${packet.lon}"
        )

        // 💡 Update notification so the victim (or someone nearby)
        // can see on the lock screen that SOS was triggered
        updateNotification("⚠️ SOS AUTO-TRIGGERED — collapse detected")

        Log.w(TAG, "Auto-SOS packet saved: ${packet.id}")
        app.timelineManager.record(
            type = TimelineEventType.AUTO_SOS_TRIGGERED,
            title = "Auto-SOS Triggered",
            detail = "LSTM collapse detection · confidence ${"%.0f".format(motion.confidence * 100)}%",
            urgency = "RED",
            lat = packetLat,
            lon = packetLon,
            packetId = packet.id,
        )
        app.buddyGroupManager.alertGroupMembers(packetLat, packetLon)

        // Give victim 30 seconds to cancel if it was a false alarm
        // TODO Step 7: show cancellable SOS UI overlay — call app.acousticBeacon.stopBeacon()
        // and pendingBeaconJob?.cancel() on cancel.

        pendingBeaconJob?.cancel()
        pendingBeaconJob = scope.launch {
            delay(5_000L)
            app.acousticBeacon.startBeacon()
            delay(3_000L)
            app.acousticBeacon.stopBeacon()
            updateNotification("SOS AUTO-TRIGGERED — beacon sounded")
            val bestLocation = app.indoorLocationManager.getCurrentBestLocation()
            val floor = app.indoorLocationManager.barometricEstimator.floorEstimate.value?.floor
            val disaster = app.disasterStateManager.currentDisasterType.value
            app.companionSessionManager.startSession(
                packetId = packet.id,
                lat = bestLocation?.lat ?: packetLat,
                lon = bestLocation?.lon ?: packetLon,
                floor = floor,
                disaster = disaster,
            )
        }
    }

    // ─────────────────────────────────────────────
    // LOCATION — keep GPS fresh for packet coordinates
    // ─────────────────────────────────────────────

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            30_000L  // update every 30 seconds
        ).build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastLocation = result.lastLocation
            Log.v(TAG, "Location updated: ${lastLocation?.latitude}, ${lastLocation?.longitude}")
            result.lastLocation?.let { loc ->
                (application as OmniMeshApp).indoorLocationManager.onGpsUpdate(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accuracy = loc.accuracy,
                    altitude = loc.altitude.toFloat()
                )
                (application as OmniMeshApp).breadcrumbTrailManager.onLocationUpdate(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accuracy = loc.accuracy
                )
            }
        }
    }

    // ─────────────────────────────────────────────
    // NOTIFICATION
    // ─────────────────────────────────────────────

    private fun buildNotification(status: String): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniMesh — Collapse Monitor")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OmniMesh Collapse Detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Monitors device sensors for collapse events" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    // ─────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pendingBeaconJob?.cancel()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        classifier.close()
        visionClassifier.close()
        scope.cancel()
        super.onDestroy()
    }
}
