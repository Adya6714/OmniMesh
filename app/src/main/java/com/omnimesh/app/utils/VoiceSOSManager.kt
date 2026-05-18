package omnimesh.command1.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "VoiceSOSManager"

/**
 * Uses Android's built-in SpeechRecognizer (free, no API key).
 * Same engine that powers Google Assistant voice input.
 */
class VoiceSOSManager(private val context: Context) {

    data class VoiceResult(
        val transcript: String,
        val confidence: Float,
        val detectedLanguage: String
    )

    suspend fun recordAndTranscribe(): VoiceResult =
        withContext(Dispatchers.Main) {
            recognizeSpeech()
        }

    private suspend fun recognizeSpeech(): VoiceResult =
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN"
                )
                putExtra(
                    RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                    arrayListOf("en-IN", "hi-IN", "ta-IN", "te-IN", "bn-IN")
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
                    val scores = results
                        ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                    val transcript = matches?.firstOrNull()
                    if (transcript != null) {
                        val confidence = scores?.firstOrNull() ?: 0.85f
                        Log.d(TAG, "Transcription: \"$transcript\" confidence=$confidence")
                        if (cont.isActive) {
                            cont.resume(
                                VoiceResult(transcript, confidence, "en-IN")
                            )
                        }
                    } else {
                        Log.w(TAG, "No transcription results")
                        if (cont.isActive) {
                            cont.resume(
                                VoiceResult("injury unknown — no speech detected", 0.3f, "en-IN")
                            )
                        }
                    }
                    recognizer.destroy()
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "no speech matched"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timed out"
                        SpeechRecognizer.ERROR_AUDIO -> "audio recording error"
                        SpeechRecognizer.ERROR_NETWORK -> "network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
                        else -> "error code $error"
                    }
                    Log.e(TAG, "SpeechRecognizer error: $msg")
                    if (cont.isActive) {
                        cont.resume(
                            VoiceResult("voice_sos — $msg", 0.1f, "en-IN")
                        )
                    }
                    recognizer.destroy()
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Listening for speech...")
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }
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
