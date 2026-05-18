package omnimesh.command1.companion

import android.content.Context
import android.util.Log
import omnimesh.command1.data.PacketRepository
import omnimesh.command1.disaster.DisasterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CompanionSessionManager(
    context: Context,
    private val repository: PacketRepository,
) {
    companion object {
        private const val TAG = "CompanionSession"
        private const val PERIODIC_UPDATE_INTERVAL_MS = 4 * 60 * 1000L
        private const val CLINICAL_EXTRACTION_INTERVAL_TURNS = 3
    }

    private val gemini = GeminiCompanion()
    val voiceManager = CompanionVoiceManager(context)
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(CompanionState.IDLE)
    val state: StateFlow<CompanionState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<CompanionMessage>>(emptyList())
    val messages: StateFlow<List<CompanionMessage>> = _messages.asStateFlow()

    private val _clinicalState = MutableStateFlow(VictimClinicalState())
    val clinicalState: StateFlow<VictimClinicalState> = _clinicalState.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var currentPacketId: String? = null
    private var sessionStartTime: Long = 0L
    private var turnCount = 0
    private var disasterType: DisasterType = DisasterType.EARTHQUAKE
    private var victimLat: Double = 0.0
    private var victimLon: Double = 0.0
    private var floorEstimate: Int? = null
    private var responderCount: Int = 0
    private var periodicUpdateJob: Job? = null

    fun init() {
        voiceManager.init()
    }

    fun shutdown() {
        periodicUpdateJob?.cancel()
        voiceManager.shutdown()
        scope.cancel()
        _state.value = CompanionState.ENDED
    }

    fun startSession(
        packetId: String,
        lat: Double,
        lon: Double,
        floor: Int?,
        disaster: DisasterType,
    ) {
        if (_state.value != CompanionState.IDLE && _state.value != CompanionState.ENDED) return

        if (_state.value == CompanionState.ENDED) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            voiceManager.init()
        }

        currentPacketId = packetId
        sessionStartTime = System.currentTimeMillis()
        victimLat = lat
        victimLon = lon
        floorEstimate = floor
        disasterType = disaster
        _state.value = CompanionState.ACTIVATING
        _clinicalState.value = VictimClinicalState()
        _messages.value = emptyList()
        turnCount = 0
        gemini.clearHistory()

        scope.launch { activateCompanion() }
    }

    fun endSession() {
        periodicUpdateJob?.cancel()
        voiceManager.stopAll()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        _state.value = CompanionState.ENDED
        _isListening.value = false
    }

    fun updateResponderCount(count: Int) {
        responderCount = count
    }

    fun updateFloorEstimate(floor: Int?) {
        floorEstimate = floor
    }

    fun sendTextMessage(text: String) {
        if (_state.value == CompanionState.IDLE || _state.value == CompanionState.ENDED) return
        scope.launch { processVictimInput(text) }
    }

    fun startVoiceInput() {
        if (_state.value == CompanionState.IDLE || _state.value == CompanionState.ENDED) return
        scope.launch {
            _isListening.value = true
            val transcript = voiceManager.listenAndTranscribe()
            _isListening.value = false
            if (transcript.isNotBlank()) {
                processVictimInput(transcript)
            } else {
                val retryMsg = "I didn't catch that. Can you speak a little louder?"
                addMessage(CompanionMessage(role = MessageRole.COMPANION, text = retryMsg))
                voiceManager.speak(retryMsg)
            }
        }
    }

    private suspend fun activateCompanion() {
        val greeting = buildGreeting()
        addMessage(CompanionMessage(role = MessageRole.COMPANION, text = greeting))
        voiceManager.speak(greeting)
        _state.value = CompanionState.ASSESSING
        startPeriodicUpdates()
        startVoiceInput()
    }

    private fun buildGreeting(): String {
        val disasterName = when (disasterType) {
            DisasterType.EARTHQUAKE -> "a collapse event"
            DisasterType.FLOOD -> "a flood event"
            DisasterType.CYCLONE -> "a cyclone"
            DisasterType.INDUSTRIAL -> "an industrial incident"
            DisasterType.NORMAL -> "an emergency"
        }
        return "OmniMesh has detected $disasterName. Your location has been transmitted to rescue teams. " +
            "I'm here with you. Can you tell me, are you able to breathe normally right now?"
    }

    private suspend fun processVictimInput(userText: String) {
        addMessage(CompanionMessage(role = MessageRole.VICTIM, text = userText))
        _state.value = CompanionState.ASSESSING
        turnCount++

        val minutesSince = ((System.currentTimeMillis() - sessionStartTime) / 60000).toInt()
        val systemPrompt = gemini.buildSystemPrompt(
            incidentType = disasterType.displayName,
            victimLat = victimLat,
            victimLon = victimLon,
            floorEstimate = floorEstimate,
            minutesSinceAlert = minutesSince,
            clinicalState = _clinicalState.value,
            responderCount = responderCount,
        )

        val response = gemini.sendMessage(userText, systemPrompt)
        addMessage(
            CompanionMessage(
                role = MessageRole.COMPANION,
                text = response.text,
                updatesTriagePacket = response.shouldEscalate
            )
        )
        voiceManager.speak(response.text)

        if (response.shouldEscalate) escalateUrgency()
        if (turnCount % CLINICAL_EXTRACTION_INTERVAL_TURNS == 0) {
            extractAndUpdateClinicalState(systemPrompt)
        }

        _state.value = CompanionState.SUPPORTING
        if (_state.value != CompanionState.ENDED) {
            delay(500)
            startVoiceInput()
        }
    }

    private suspend fun extractAndUpdateClinicalState(systemPrompt: String) {
        val extracted = gemini.extractClinicalState(systemPrompt)
        _clinicalState.value = extracted
        currentPacketId?.let { packetId ->
            val updatedInjuryText = extracted.toInjuryString()
            val updatedUrgency = extracted.computeUrgency()
            scope.launch(Dispatchers.IO) {
                repository.updatePacketFromCompanion(
                    packetId = packetId,
                    injuryText = updatedInjuryText,
                    urgency = updatedUrgency,
                    signalSources = "MV+C"
                )
            }
            Log.d(TAG, "Updated packet $packetId: urgency=$updatedUrgency injury=$updatedInjuryText")
        }
    }

    private fun escalateUrgency() {
        currentPacketId?.let { packetId ->
            scope.launch(Dispatchers.IO) {
                repository.updatePacketUrgency(packetId, "RED")
            }
        }
        Log.w(TAG, "ESCALATING urgency to RED based on companion conversation")
    }

    private fun startPeriodicUpdates() {
        periodicUpdateJob?.cancel()
        periodicUpdateJob = scope.launch {
            delay(PERIODIC_UPDATE_INTERVAL_MS)
            while (isActive && _state.value != CompanionState.ENDED) {
                val minutesSince = ((System.currentTimeMillis() - sessionStartTime) / 60000).toInt()
                val updateMsg =
                    "It's been $minutesSince minutes since your alert was sent. Rescue teams have been notified and are working toward you. You're doing well. How are you feeling right now?"
                addMessage(CompanionMessage(role = MessageRole.COMPANION, text = updateMsg))
                voiceManager.speakAsync(updateMsg)
                delay(PERIODIC_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun addMessage(message: CompanionMessage) {
        _messages.value = _messages.value + message
    }
}
