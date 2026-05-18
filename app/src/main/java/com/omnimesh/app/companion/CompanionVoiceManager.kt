package omnimesh.command1.companion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class CompanionVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "CompanionVoice"
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                isTtsReady = true
                Log.d(TAG, "TTS initialized successfully")
            }
        }
    }

    fun stopAll() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
    }

    suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        if (!isTtsReady) {
            delay(500)
            if (!isTtsReady) return@withContext
        }
        val utteranceId = "omnimesh_${System.currentTimeMillis()}"
        _isSpeaking.value = true

        val completion = CompletableDeferred<Unit>()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                completion.complete(Unit)
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                completion.complete(Unit)
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        completion.await()
    }

    fun speakAsync(text: String) {
        scope.launch { speak(text) }
    }

    suspend fun listenAndTranscribe(): String = withContext(Dispatchers.Main) {
        _isListening.value = true
        try {
            recognizeSpeech()
        } finally {
            _isListening.value = false
        }
    }

    private suspend fun recognizeSpeech(): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(
                    RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                    arrayListOf("en-IN", "hi-IN", "ta-IN", "te-IN")
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    3000L
                )
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Transcription: \"$transcript\"")
                    if (cont.isActive) cont.resume(transcript)
                    recognizer.destroy()
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "no speech matched"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timed out"
                        SpeechRecognizer.ERROR_AUDIO -> "audio error"
                        SpeechRecognizer.ERROR_NETWORK -> "network error"
                        else -> "error code $error"
                    }
                    Log.w(TAG, "SpeechRecognizer error: $msg")
                    if (cont.isActive) cont.resume("")
                    recognizer.destroy()
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Listening...")
                }
                override fun onBeginningOfSpeech() {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended, processing...")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            cont.invokeOnCancellation {
                recognizer.cancel()
                recognizer.destroy()
            }

            recognizer.startListening(intent)
        }
}
