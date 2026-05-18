package omnimesh.command1.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnimesh.app.ml.VisionClassifier
import com.omnimesh.app.ml.VisionSignal
import omnimesh.command1.companion.CompanionMessage
import omnimesh.command1.companion.CompanionState
import omnimesh.command1.companion.StartTriageResult
import omnimesh.command1.companion.VictimClinicalState
import omnimesh.command1.command.BuddyGroup
import omnimesh.command1.command.SpikeAlert
import omnimesh.command1.command.TimelineEvent
import omnimesh.command1.command.TimelineEventType
import omnimesh.command1.LastCaptureImagePathStore
import omnimesh.command1.OmniMeshApp
import omnimesh.command1.data.TriagePacket
import omnimesh.command1.ml.DispatchAgent
import omnimesh.command1.responder.DangerZone
import omnimesh.command1.responder.SectorClaim
import omnimesh.command1.responder.BreadcrumbWaypoint
import omnimesh.command1.utils.VoiceSOSManager
import omnimesh.command1.utils.DeviceUtils
import omnimesh.command1.location.IndoorLocation
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val SOS_DEBUG_TAG = "SOS_DEBUG"

data class ManualSosRequest(
    val urgency: String = "RED",
    val injury: String = "Unknown critical injury",
    val note: String = "",
    val unableToSpeak: Boolean = false,
)

// 💡 ViewModel survives screen rotations. If MainActivity is recreated
// (screen rotated, language changed), the ViewModel keeps its state.
// All UI state lives here — never in the Activity or Composable directly.
class OmniMeshViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OmniMeshApp

    // App mode
    private val _mode = MutableStateFlow(AppMode.VICTIM)
    val mode: StateFlow<AppMode> = _mode

    // Live packet list from Room (auto-updates when DB changes)
    val packets = app.repository.observeAllPackets()

    // SOS state
    private val _sosState = MutableStateFlow(SOSState.IDLE)
    val sosState: StateFlow<SOSState> = _sosState

    /** Shown under the SOS button after a send completes, so users know the packet was queued. */
    private val _lastManualSosAck = MutableStateFlow<String?>(null)
    val lastManualSosAck: StateFlow<String?> = _lastManualSosAck

    // Dispatch agent output
    private val _dispatchResult = MutableStateFlow<DispatchAgent.DispatchRecommendation?>(null)
    val dispatchResult: StateFlow<DispatchAgent.DispatchRecommendation?> = _dispatchResult

    private val _commandMetricHistory = MutableStateFlow<Map<String, List<Int>>>(
        mapOf(
            "TOTAL" to emptyList<Int>(),
            "CRITICAL" to emptyList<Int>(),
            "AUTO-SOS" to emptyList<Int>()
        )
    )
    val commandMetricHistory: StateFlow<Map<String, List<Int>>> = _commandMetricHistory

    // Mesh peer count
    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount

    val indoorLocation: StateFlow<IndoorLocation?> = app.indoorLocationManager.bestLocation

    private val _isBeaconActive = MutableStateFlow(false)
    val isBeaconActive: StateFlow<Boolean> = _isBeaconActive
    val companionState: StateFlow<CompanionState> = app.companionSessionManager.state
    val companionMessages: StateFlow<List<CompanionMessage>> = app.companionSessionManager.messages
    val companionClinicalState: StateFlow<VictimClinicalState> = app.companionSessionManager.clinicalState
    val companionIsListening: StateFlow<Boolean> = app.companionSessionManager.isListening
    private val breadcrumbManager = app.breadcrumbTrailManager
    private val structuralDetector = app.structuralDangerDetector
    val walkieTalkie = app.walkieTalkieManager

    val isWalkieTalkieTransmitting: StateFlow<Boolean> = walkieTalkie.isTransmitting
    val isWalkieTalkieReceiving: StateFlow<Boolean> = walkieTalkie.isReceiving
    val activeSpeaker: StateFlow<String?> = walkieTalkie.activeSpeaker
    private val currentIncidentId = "default_incident"
    val breadcrumbWaypoints: Flow<List<BreadcrumbWaypoint>> =
        breadcrumbManager.observeWaypoints(currentIncidentId)
    val sectorClaims: Flow<List<SectorClaim>> =
        breadcrumbManager.observeSectorClaims(currentIncidentId)
    val activeDangerZones: Flow<List<DangerZone>> =
        structuralDetector.observeActiveDangerZones(currentIncidentId)
    private val timeline = app.timelineManager
    private val buddyManager = app.buddyGroupManager
    val timelineEvents: Flow<List<TimelineEvent>> = timeline.observeEvents(currentIncidentId)
    val spikeAlert: StateFlow<SpikeAlert?> = timeline.spikeAlert
    val buddyGroup: StateFlow<BuddyGroup?> = buddyManager.myGroup
    val memberLocations = buddyManager.memberLocations

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    private suspend fun getDeviceLocation(): Pair<Double, Double> {
        val indoor = indoorLocation.value
        if (indoor != null) return indoor.lat to indoor.lon

        return try {
            val loc = fusedLocationClient.lastLocation.await()
            if (loc != null) loc.latitude to loc.longitude else 0.0 to 0.0
        } catch (_: SecurityException) {
            0.0 to 0.0
        }
    }

    init {
        viewModelScope.launch {
            while (true) {
                _isBeaconActive.value = app.acousticBeacon.isActive
                delay(2000)
            }
        }
    }

    // Voice recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val voiceManager = VoiceSOSManager(application)

    private val visionClassifier = VisionClassifier(application)

    /** Last camera capture path; cleared when a new file is created. Fusion/triage can read this. */
    private var currentImagePath: String? = null

    private val _visionSignal = MutableStateFlow<VisionSignal?>(null)
    val visionSignal: StateFlow<VisionSignal?> = _visionSignal

    fun setMode(mode: AppMode) { _mode.value = mode }

    fun createImageFile(context: Context): File {
        val imageFile = File(
            context.cacheDir,
            "omnimesh_capture_${System.currentTimeMillis()}.jpg"
        )
        currentImagePath = imageFile.absolutePath
        LastCaptureImagePathStore.set(currentImagePath)
        return imageFile
    }

    fun onImageCaptured() {
        viewModelScope.launch {
            val path = currentImagePath
            val visionResult = visionClassifier.classify(path)
            _visionSignal.value = visionResult
        }
    }

    /** Path of the latest injury photo for the next triage / fusion run (may be null). */
    fun getCurrentImagePath(): String? = currentImagePath

    fun triggerManualSOS(request: ManualSosRequest) {
        viewModelScope.launch {
            _lastManualSosAck.value = null
            _sosState.value = SOSState.TRIGGERED
            val location = indoorLocation.value
            val (deviceLat, deviceLon) = getDeviceLocation()
            val packet = TriagePacket(
                id = "manual_${System.currentTimeMillis()}",
                urgency = request.urgency,
                injury = buildManualInjurySummary(request),
                loc = buildManualLocationLabel(location),
                lat = deviceLat,
                lon = deviceLon,
                ts = System.currentTimeMillis(),
                confidence = if (request.unableToSpeak) 0.97f else 0.92f,
                signalSources = buildManualSignalSources(request),
                isAutoGenerated = false,
                originDeviceId = DeviceUtils.getDeviceId(getApplication())
            )
            Log.d(
                SOS_DEBUG_TAG,
                "Manual SOS packet created id=${packet.id} urgency=${packet.urgency} lat=${packet.lat} lon=${packet.lon}"
            )
            app.repository.save(packet)
            _sosState.value = SOSState.TRANSMITTED
            delay(3000)
            _sosState.value = SOSState.IDLE
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(System.currentTimeMillis())
            val locationStatus = if (location != null) "live location attached" else "fallback location attached"
            _lastManualSosAck.value =
                "SOS packet sent — ${request.injury} · $locationStatus · $time"
            delay(8000)
            _lastManualSosAck.value = null
        }
    }

    fun triggerVoiceSOS() {
        viewModelScope.launch {
            _isRecording.value = true
            _sosState.value = SOSState.RECORDING

            try {
                val (deviceLat, deviceLon) = getDeviceLocation()
                val voiceResult = voiceManager.recordAndTranscribe()
                val packet = TriagePacket(
                    id = java.util.UUID.randomUUID().toString().take(8),
                    urgency = "RED",
                    injury = voiceResult.transcript.take(50),
                    loc = "voice_sos",
                    lat = deviceLat, lon = deviceLon,
                    ts = System.currentTimeMillis(),
                    confidence = voiceResult.confidence,
                    signalSources = "VOICE",
                    isAutoGenerated = false
                )
                Log.d(
                    SOS_DEBUG_TAG,
                    "Voice SOS packet created id=${packet.id} urgency=${packet.urgency} lat=${packet.lat} lon=${packet.lon}"
                )
                app.repository.save(packet)
                _sosState.value = SOSState.TRANSMITTED
            } catch (e: Exception) {
                Log.e(SOS_DEBUG_TAG, "Voice SOS failed", e)
                _sosState.value = SOSState.IDLE
            } finally {
                _isRecording.value = false
            }
        }
    }

    fun runDispatchAgent() {
        viewModelScope.launch {
            val allPackets = app.repository.getAllPackets()
            val result = app.dispatchAgent.analyzeZone(allPackets)
            _dispatchResult.value = result
            timeline.record(
                type = TimelineEventType.GEMINI_ANALYSIS_UPDATED,
                title = "Gemini Analysis Updated",
                detail = "Analyzed ${allPackets.size} casualties · ${result.zoneAssignments.size} zones assigned",
            )
        }
    }

    fun updatePeerCount(count: Int) { _peerCount.value = count }

    fun pushCommandMetricSnapshot(total: Int, critical: Int, autoSos: Int) {
        val current = _commandMetricHistory.value
        _commandMetricHistory.value = current + mapOf(
            "TOTAL" to ((current["TOTAL"].orEmpty() + total).takeLast(6)),
            "CRITICAL" to ((current["CRITICAL"].orEmpty() + critical).takeLast(6)),
            "AUTO-SOS" to ((current["AUTO-SOS"].orEmpty() + autoSos).takeLast(6))
        )
    }

    fun stopAcousticBeacon() {
        app.acousticBeacon.stopBeacon()
    }

    fun sendCompanionMessage(text: String) {
        app.companionSessionManager.sendTextMessage(text)
    }

    fun startCompanionVoiceInput() {
        app.companionSessionManager.startVoiceInput()
    }

    fun endCompanionSession() {
        app.companionSessionManager.endSession()
    }

    fun debugStartCompanion() {
        viewModelScope.launch {
            val (lat, lon) = getDeviceLocation()
            app.companionSessionManager.startSession(
                packetId = "debug_${System.currentTimeMillis()}",
                lat = lat,
                lon = lon,
                floor = null,
                disaster = app.disasterStateManager.currentDisasterType.value,
            )
        }
    }

    fun submitStartTriageResult(result: StartTriageResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val latest = app.repository.getTransmitQueue().firstOrNull() ?: return@launch
            app.repository.updatePacketFromCompanion(
                packetId = latest.id,
                injuryText = "${result.derivedCategory.description}. " +
                    "Walk: ${result.canWalk}, Breathing: ${result.respirationsPerMinute}, " +
                    "Pulse: ${result.radialPulsePresent}",
                urgency = result.derivedCategory.color.takeIf {
                    it in listOf("RED", "YELLOW", "GREEN", "BLACK")
                } ?: "GREEN",
                signalSources = "START"
            )
        }
    }

    fun startFieldTracking() {
        breadcrumbManager.startTracking(currentIncidentId)
        breadcrumbManager.responderName = "Responder_${
            DeviceUtils.getDeviceId(getApplication()).take(4)
        }"
    }

    fun stopFieldTracking() {
        breadcrumbManager.stopTracking()
    }

    fun claimSector(lat: Double, lon: Double, label: String) {
        breadcrumbManager.claimSector(lat, lon, label)
    }

    fun clearSector(claimId: String) {
        breadcrumbManager.clearSector(claimId)
    }

    fun startPtt() {
        walkieTalkie.startTransmitting()
    }

    fun stopPtt() {
        walkieTalkie.stopTransmitting()
    }

    fun generateQrForPacket(packet: TriagePacket): Flow<android.graphics.Bitmap?> = flow {
        val bitmap = app.qrTriageCardManager.generateQrBitmap(packet)
        emit(bitmap)
    }.flowOn(Dispatchers.IO)

    fun resolveScannedQr(payload: String): Flow<TriagePacket?> = flow {
        val packet = app.qrTriageCardManager.resolveScannedQr(payload)
        emit(packet)
    }.flowOn(Dispatchers.IO)

    fun dismissSpikeAlert() = timeline.dismissSpikeAlert()

    private val _lastJoinCode = MutableStateFlow<String?>(null)
    val lastJoinCode: StateFlow<String?> = _lastJoinCode

    fun createBuddyGroup(name: String, displayName: String) {
        viewModelScope.launch {
            val code = buddyManager.createGroup(name, displayName)
            _lastJoinCode.value = code
        }
    }

    fun joinBuddyGroup(code: String, displayName: String) {
        viewModelScope.launch {
            buddyManager.joinGroup(code, displayName)
        }
    }

    fun leaveBuddyGroup() {
        buddyManager.leaveGroup()
        _lastJoinCode.value = null
    }

    fun markResponderReached(packetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            timeline.record(
                type = TimelineEventType.RESPONDER_REACHED_VICTIM,
                title = "Responder Reached Victim",
                detail = "Responder arrived at victim location — packet $packetId",
                packetId = packetId,
            )
        }
    }

    fun markPacketConfirmed(packetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val packet = app.repository.getPacketById(packetId) ?: return@launch
            app.repository.updatePacketFromCompanion(
                packetId = packetId,
                injuryText = packet.injury,
                urgency = packet.urgency,
                signalSources = "${packet.signalSources}+CONFIRMED"
            )
            timeline.record(
                type = TimelineEventType.VICTIM_CONFIRMED,
                title = "Victim Confirmed",
                detail = "Responder physically confirmed victim at packet $packetId",
                packetId = packetId,
            )
        }
    }

    fun markPacketFalsePositive(packetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.repository.updatePacketFromCompanion(
                packetId = packetId,
                injuryText = "FALSE POSITIVE",
                urgency = "GREEN",
                signalSources = "FALSE"
            )
            timeline.record(
                type = TimelineEventType.FALSE_POSITIVE_REPORTED,
                title = "False Positive Reported",
                detail = "Responder marked packet $packetId as false positive",
                packetId = packetId,
            )
        }
    }

    private fun buildManualLocationLabel(location: omnimesh.command1.location.IndoorLocation?): String {
        if (location == null) return "manual_location_pending"
        val floorLabel = location.estimatedFloor?.let { "floor_$it" } ?: "floor_unknown"
        val methodLabel = location.method.name.lowercase()
        return "${floorLabel}_${methodLabel}"
    }

    private fun buildManualSignalSources(request: ManualSosRequest): String = buildList {
        add("MANUAL")
        if (request.note.isNotBlank()) add("TEXT")
        if (request.unableToSpeak) add("NO_VOICE")
    }.joinToString("+")

    private fun buildManualInjurySummary(request: ManualSosRequest): String = buildList {
        add(request.injury)
        request.note.trim().takeIf { it.isNotEmpty() }?.let { add("Note: $it") }
        if (request.unableToSpeak) add("Unable to speak")
    }.joinToString(" · ")
}

enum class AppMode { VICTIM, RESPONDER, COMMAND }
enum class SOSState { IDLE, RECORDING, TRIGGERED, TRANSMITTED }
