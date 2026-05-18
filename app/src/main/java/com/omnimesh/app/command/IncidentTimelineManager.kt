package omnimesh.command1.command

import android.content.Context
import android.util.Log
import omnimesh.command1.data.OmniMeshDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IncidentTimelineManager(private val context: Context) {

    companion object {
        private const val TAG = "IncidentTimeline"
        private const val SPIKE_WINDOW_MS = 3 * 60 * 1000L
        private const val SPIKE_THRESHOLD = 3
        private const val CURRENT_INCIDENT = "default_incident"
    }

    private val dao = OmniMeshDatabase.getInstance(context).timelineDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _spikeAlert = MutableStateFlow<SpikeAlert?>(null)
    val spikeAlert: StateFlow<SpikeAlert?> = _spikeAlert.asStateFlow()

    fun observeEvents(incidentId: String): Flow<List<TimelineEvent>> = dao.observeEvents(incidentId)

    fun record(
        type: TimelineEventType,
        title: String,
        detail: String,
        urgency: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        packetId: String? = null,
        deviceId: String? = null,
    ) {
        scope.launch {
            val event = TimelineEvent(
                incidentId = CURRENT_INCIDENT,
                type = type,
                title = title,
                detail = detail,
                urgency = urgency,
                lat = lat,
                lon = lon,
                packetId = packetId,
                deviceId = deviceId,
            )
            dao.insertEvent(event)
            Log.d(TAG, "Timeline: [$type] $title")
            if (type == TimelineEventType.RED_PACKET_DETECTED ||
                type == TimelineEventType.AUTO_SOS_TRIGGERED
            ) {
                checkForSpike()
            }
        }
    }

    private suspend fun checkForSpike() {
        val now = System.currentTimeMillis()
        val windowStart = now - SPIKE_WINDOW_MS
        val count = dao.countRedEventsInWindow(CURRENT_INCIDENT, windowStart, now)
        if (count >= SPIKE_THRESHOLD) {
            val minutesAgo = SPIKE_WINDOW_MS / 60000
            _spikeAlert.value = SpikeAlert(
                message = "$count new critical detections in the last ${minutesAgo}min — possible secondary collapse or undiscovered zone",
                eventCount = count,
                windowMinutes = (SPIKE_WINDOW_MS / 60000).toInt(),
                detectedAt = now,
            )
            val spikeEvent = TimelineEvent(
                incidentId = CURRENT_INCIDENT,
                type = TimelineEventType.STRUCTURAL_WARNING,
                title = "SPIKE DETECTED",
                detail = "$count RED events in ${minutesAgo}min window — investigate for secondary collapse",
            )
            dao.insertEvent(spikeEvent)
            Log.w(TAG, "Spike alert: $count RED events in ${minutesAgo}min")
        }
    }

    fun dismissSpikeAlert() {
        _spikeAlert.value = null
    }
}

data class SpikeAlert(
    val message: String,
    val eventCount: Int,
    val windowMinutes: Int,
    val detectedAt: Long,
)
