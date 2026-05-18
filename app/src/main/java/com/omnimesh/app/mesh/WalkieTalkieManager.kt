package omnimesh.command1.mesh

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import omnimesh.command1.responder.AudioChunk
import omnimesh.command1.utils.DeviceUtils
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

class WalkieTalkieManager(
    private val context: Context,
    private val meshManager: NearbyMeshManager,
) {

    companion object {
        private const val TAG = "WalkieTalkie"
        private const val SAMPLE_RATE = 8000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_DURATION_MS = 160
        private const val FRAME_SIZE_BYTES =
            (SAMPLE_RATE * FRAME_DURATION_MS / 1000) * 2

        const val AUDIO_PAYLOAD_PREFIX = "AUDIO|"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deviceId = DeviceUtils.getDeviceId(context)
    private val deviceName = DeviceUtils.getEndpointName(context)

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _activeSpeaker = MutableStateFlow<String?>(null)
    val activeSpeaker: StateFlow<String?> = _activeSpeaker.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var transmitJob: Job? = null
    private var sequenceNumber = 0

    fun startTransmitting() {
        if (_isTransmitting.value) return
        _isTransmitting.value = true
        Log.d(TAG, "PTT: start transmitting")

        transmitJob = scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_IN, ENCODING
            ).coerceAtLeast(FRAME_SIZE_BYTES)

            audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Microphone permission denied for walkie-talkie")
                _isTransmitting.value = false
                return@launch
            }

            audioRecord?.startRecording()
            val buffer = ByteArray(FRAME_SIZE_BYTES)

            try {
                while (isActive && _isTransmitting.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, FRAME_SIZE_BYTES) ?: break
                    if (bytesRead > 0) {
                        val chunk = AudioChunk(
                            senderId = deviceId,
                            senderName = deviceName,
                            sequenceNumber = sequenceNumber++,
                            audioData = buffer.copyOf(bytesRead),
                        )
                        meshManager.broadcastAudioChunk(chunk)
                    }
                }
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
    }

    fun stopTransmitting() {
        _isTransmitting.value = false
        transmitJob?.cancel()
        transmitJob = null
        Log.d(TAG, "PTT: stop transmitting")
    }

    fun onAudioPayloadReceived(bytes: ByteArray) {
        val chunk = AudioChunk.fromBytes(bytes) ?: return
        if (chunk.senderId == deviceId) return

        _isReceiving.value = true
        _activeSpeaker.value = chunk.senderName
        scope.launch {
            playAudioChunk(chunk.audioData)
            delay(500)
            if (_activeSpeaker.value == chunk.senderName) {
                _activeSpeaker.value = null
                _isReceiving.value = false
            }
        }
    }

    private fun playAudioChunk(audioData: ByteArray) {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_OUT, ENCODING
            ).coerceAtLeast(audioData.size)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(audioData, 0, audioData.size)
            track.play()
            Thread.sleep((FRAME_DURATION_MS + 20).toLong())
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.w(TAG, "Audio playback failed: ${e.message}")
        }
    }

    fun shutdown() {
        stopTransmitting()
        scope.cancel()
    }
}
