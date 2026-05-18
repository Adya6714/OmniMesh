package omnimesh.command1.responder

import android.content.Context
import android.util.Log
import omnimesh.command1.data.OmniMeshDatabase
import omnimesh.command1.utils.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StructuralDangerDetector(private val context: Context) {

    companion object {
        private const val TAG = "StructuralDanger"
        private const val STRUCTURAL_CLASS_INDEX = 2
        private const val DANGER_THRESHOLD = 0.70f
        private const val COOLDOWN_MS = 60_000L
    }

    private val dao = OmniMeshDatabase.getInstance(context).responderDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeDangerZone = MutableStateFlow<DangerZone?>(null)
    val activeDangerZone: StateFlow<DangerZone?> = _activeDangerZone.asStateFlow()

    private var lastAlertTime: Long = 0L
    var currentIncidentId: String = "default_incident"
    var currentLat: Double = 0.0
    var currentLon: Double = 0.0

    fun onAudioInference(audioProbabilities: FloatArray) {
        if (audioProbabilities.size <= STRUCTURAL_CLASS_INDEX) return
        val structuralConfidence = audioProbabilities[STRUCTURAL_CLASS_INDEX]

        if (structuralConfidence >= DANGER_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastAlertTime < COOLDOWN_MS) return
            lastAlertTime = now

            Log.w(TAG, "STRUCTURAL STRESS DETECTED: confidence=$structuralConfidence")
            createDangerZone(structuralConfidence)
        }
    }

    private fun createDangerZone(confidence: Float) {
        val zone = DangerZone(
            incidentId = currentIncidentId,
            detectedByDeviceId = DeviceUtils.getDeviceId(context),
            centerLat = currentLat,
            centerLon = currentLon,
            radiusMeters = 25f,
            audioConfidence = confidence,
        )
        scope.launch {
            dao.insertDangerZone(zone)
            _activeDangerZone.value = zone
            Log.w(TAG, "Danger zone created at ($currentLat, $currentLon) r=25m")
        }
    }

    fun deactivateZone(zoneId: String) {
        scope.launch {
            dao.deactivateDangerZone(zoneId)
            if (_activeDangerZone.value?.id == zoneId) {
                _activeDangerZone.value = null
            }
        }
    }

    fun observeActiveDangerZones(incidentId: String): Flow<List<DangerZone>> =
        dao.observeActiveDangerZones(incidentId)
}
