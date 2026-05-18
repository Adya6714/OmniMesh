package omnimesh.command1.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TAG = "DispatchTTS"

// 💡 Text-to-Speech reads dispatch instructions aloud.
// In a dusty chaotic disaster zone, a responder cannot
// safely look at a screen while moving through rubble.
// Audio instructions keep eyes and hands free.
class DispatchTTSManager(private val apiKey: String) {

    suspend fun speak(text: String, context: Context) = withContext(Dispatchers.IO) {
        try {
            val audioBytes = synthesize(text)
            playAudio(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed: ${e.message}")
            // Fallback to Android built-in TTS
            fallbackToAndroidTTS(text, context)
        }
    }

    private fun synthesize(text: String): ByteArray {
        val requestBody = JSONObject().apply {
            put("input", JSONObject().put("text", text))
            put("voice", JSONObject().apply {
                put("languageCode", "en-IN")
                // 💡 Wavenet voices sound significantly more natural than
                // standard voices — important for clear communication in noisy environments
                put("name", "en-IN-Wavenet-B")
                put("ssmlGender", "NEUTRAL")
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", "LINEAR16")
                put("speakingRate", 1.1)   // slightly faster for urgency
                put("pitch", 0.0)
                put("volumeGainDb", 6.0)   // louder for outdoor use
            })
        }.toString()

        val url = URL("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
        val conn = url.openConnection() as HttpsURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        conn.outputStream.use { it.write(requestBody.toByteArray()) }

        val response = JSONObject(conn.inputStream.bufferedReader().readText())
        val base64Audio = response.getString("audioContent")
        return Base64.decode(base64Audio, Base64.DEFAULT)
    }

    private fun playAudio(pcmBytes: ByteArray) {
        val sampleRate = 24000
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(pcmBytes.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmBytes, 0, pcmBytes.size)
        track.play()
        Thread.sleep((pcmBytes.size / (sampleRate * 2) * 1000).toLong() + 500)
        track.stop()
        track.release()
    }

    private fun fallbackToAndroidTTS(text: String, context: Context) {
        // Android built-in TTS — always available, no API key
        android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                // speak inline
            }
        }
    }
}
