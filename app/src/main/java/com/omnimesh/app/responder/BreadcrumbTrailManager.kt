package omnimesh.command1.responder

import android.content.Context
import android.util.Log
import omnimesh.command1.data.OmniMeshDatabase
import omnimesh.command1.utils.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class BreadcrumbTrailManager(private val context: Context) {

    companion object {
        private const val TAG = "BreadcrumbTrail"
        private const val MIN_DISTANCE_METERS = 3f
    }

    private val dao = OmniMeshDatabase.getInstance(context).responderDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null

    val responderId: String = DeviceUtils.getDeviceId(context)
    var currentIncidentId: String = "default_incident"
    var responderName: String = "Responder_${responderId.take(4)}"

    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0
    private var isTracking = false

    fun startTracking(incidentId: String) {
        if (isTracking) return
        currentIncidentId = incidentId
        isTracking = true
        Log.d(TAG, "Breadcrumb tracking started for incident $incidentId")
    }

    fun stopTracking() {
        isTracking = false
        trackingJob?.cancel()
        Log.d(TAG, "Breadcrumb tracking stopped")
    }

    fun onLocationUpdate(lat: Double, lon: Double, accuracy: Float) {
        if (!isTracking) return

        val distance = haversineMeters(lastLat, lastLon, lat, lon)
        if (lastLat == 0.0 || distance >= MIN_DISTANCE_METERS) {
            lastLat = lat
            lastLon = lon

            scope.launch {
                val waypoint = BreadcrumbWaypoint(
                    responderId = responderId,
                    incidentId = currentIncidentId,
                    lat = lat,
                    lon = lon,
                    accuracyMeters = accuracy,
                )
                dao.insertWaypoint(waypoint)
                Log.d(TAG, "Waypoint recorded: ($lat, $lon)")
            }
        }
    }

    fun claimSector(
        lat: Double,
        lon: Double,
        label: String,
        radiusMeters: Float = 30f
    ): SectorClaim {
        val claim = SectorClaim(
            incidentId = currentIncidentId,
            responderId = responderId,
            responderName = responderName,
            centerLat = lat,
            centerLon = lon,
            radiusMeters = radiusMeters,
            label = label,
        )
        scope.launch { dao.insertSectorClaim(claim) }
        Log.d(TAG, "Sector claimed: $label at ($lat, $lon)")
        return claim
    }

    fun clearSector(claimId: String) {
        scope.launch {
            dao.updateSectorStatus(
                id = claimId,
                status = SectorStatus.CLEARED,
                clearedAt = System.currentTimeMillis()
            )
        }
    }

    fun observeWaypoints(incidentId: String): Flow<List<BreadcrumbWaypoint>> =
        dao.observeWaypoints(incidentId)

    fun observeSectorClaims(incidentId: String): Flow<List<SectorClaim>> =
        dao.observeSectorClaims(incidentId)

    private fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return (r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }
}
