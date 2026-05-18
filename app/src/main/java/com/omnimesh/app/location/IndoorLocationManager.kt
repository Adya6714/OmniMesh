package omnimesh.command1.location

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates indoor location estimation from:
 * 1. GPS (when available, outdoors)
 * 2. RSSI observations from mesh peers (when GPS is poor, indoors)
 * 3. Barometric pressure (for floor estimation)
 *
 * This manager is fed peer RSSI observations by NearbyMeshManager
 * and GPS updates by CollapseDetectorService.
 */
class IndoorLocationManager(context: Context) {

    companion object {
        private const val TAG = "IndoorLocationManager"
        private const val RSSI_OBSERVATION_MAX_AGE_MS = 30_000L // 30 seconds
        private const val MIN_GPS_ACCURACY_METERS = 15f // below this GPS is trusted
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val barometricEstimator = BarometricFloorEstimator(context)

    // Current observations from mesh peers
    private val peerObservations = ConcurrentHashMap<String, PeerRssiObservation>()

    // Last known GPS fix
    private var lastGpsLat: Double = 0.0
    private var lastGpsLon: Double = 0.0
    private var lastGpsAccuracy: Float = Float.MAX_VALUE
    private var lastGpsTimestamp: Long = 0L

    private val _bestLocation = MutableStateFlow<IndoorLocation?>(null)
    val bestLocation: StateFlow<IndoorLocation?> = _bestLocation.asStateFlow()

    fun start() {
        barometricEstimator.start()
    }

    fun stop() {
        barometricEstimator.stop()
    }

    /**
     * Called by CollapseDetectorService when GPS updates.
     * If accuracy is good, use GPS directly and calibrate barometer.
     */
    fun onGpsUpdate(lat: Double, lon: Double, accuracy: Float, altitude: Float) {
        lastGpsLat = lat
        lastGpsLon = lon
        lastGpsAccuracy = accuracy
        lastGpsTimestamp = System.currentTimeMillis()

        // Calibrate barometer when we have a good outdoor GPS fix
        if (accuracy < MIN_GPS_ACCURACY_METERS) {
            barometricEstimator.calibrateFromAltitude(altitude)
        }

        recomputeBestLocation()
    }

    /**
     * Called by NearbyMeshManager when we receive a RSSI observation from a peer.
     * The peer's packet includes their GPS location and we measure their RSSI.
     */
    fun onPeerRssiObservation(observation: PeerRssiObservation) {
        peerObservations[observation.peerId] = observation
        Log.d(
            TAG,
            "RSSI observation from ${observation.peerId}: " +
                "${observation.rssi} dBm = ${observation.estimatedDistanceMeters}m"
        )

        // Prune stale observations
        val cutoff = System.currentTimeMillis() - RSSI_OBSERVATION_MAX_AGE_MS
        peerObservations.entries.removeIf { it.value.observedAt < cutoff }

        recomputeBestLocation()
    }

    /**
     * Determines the best available location estimate and publishes it.
     * Priority: GPS (if good) > RSSI triangulation > last known
     */
    private fun recomputeBestLocation() {
        scope.launch {
            val floor = barometricEstimator.floorEstimate.value
            val recentObservations = peerObservations.values
                .filter { System.currentTimeMillis() - it.observedAt < RSSI_OBSERVATION_MAX_AGE_MS }
                .toList()

            val location = when {
                // Good GPS fix available
                lastGpsAccuracy < MIN_GPS_ACCURACY_METERS &&
                    System.currentTimeMillis() - lastGpsTimestamp < 60_000L -> {
                    IndoorLocation(
                        lat = lastGpsLat,
                        lon = lastGpsLon,
                        accuracyMeters = lastGpsAccuracy,
                        estimatedFloor = floor?.floor,
                        floorConfidence = floor?.confidence ?: 0f,
                        method = if (floor != null) LocationMethod.GPS_PLUS_FLOOR
                        else LocationMethod.GPS,
                    )
                }

                // Enough peers for triangulation
                recentObservations.size >= RssiTriangulator.MIN_PEERS_FOR_TRIANGULATION -> {
                    val triangulated = RssiTriangulator.triangulate(
                        recentObservations,
                        isCollapseScenario = true
                    )
                    triangulated?.copy(
                        estimatedFloor = floor?.floor,
                        floorConfidence = floor?.confidence ?: 0f,
                    )
                }

                // One or two peers — single-peer rough estimate
                recentObservations.isNotEmpty() -> {
                    val closest = recentObservations.minByOrNull { it.estimatedDistanceMeters }!!
                    IndoorLocation(
                        lat = closest.peerLat,
                        lon = closest.peerLon,
                        accuracyMeters = closest.estimatedDistanceMeters.toFloat() * 2,
                        estimatedFloor = floor?.floor,
                        floorConfidence = floor?.confidence ?: 0f,
                        method = LocationMethod.RSSI_SINGLE_PEER,
                        contributingPeerCount = recentObservations.size,
                    )
                }

                // Fall back to last known GPS
                lastGpsLat != 0.0 -> {
                    IndoorLocation(
                        lat = lastGpsLat,
                        lon = lastGpsLon,
                        accuracyMeters = 50f,
                        estimatedFloor = floor?.floor,
                        floorConfidence = floor?.confidence ?: 0f,
                        method = LocationMethod.LAST_KNOWN,
                    )
                }

                else -> null
            }

            _bestLocation.value = location
            location?.let {
                Log.d(
                    TAG,
                    "Best location: (${it.lat}, ${it.lon}) " +
                        "±${it.accuracyMeters}m floor=${it.estimatedFloor} " +
                        "method=${it.method}"
                )
            }
        }
    }

    fun getCurrentBestLocation(): IndoorLocation? = _bestLocation.value
}
