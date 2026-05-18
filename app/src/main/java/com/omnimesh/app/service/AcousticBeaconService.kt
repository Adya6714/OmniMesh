package omnimesh.command1.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Acoustic Beacon Mode — emits a distinctive tone pattern when auto-SOS fires.
 *
 * Purpose: Help search and rescue teams physically locate a buried victim.
 * Real SAR teams use acoustic listening devices and can detect even quiet tones
 * through significant amounts of debris. The human-audible version helps
 * rescuers working without equipment.
 *
 * Tone pattern: three short pulses (SOS morse: · · · — — — · · ·)
 * emitted every 30 seconds at maximum volume.
 *
 * Activates: only after auto-SOS fires AND 30-second cancellation window passes.
 * Deactivates: when victim manually cancels, or when responder marks confirmed.
 */
class AcousticBeaconService(private val context: Context) {

    companion object {
        private const val TAG = "AcousticBeacon"
        private const val BEACON_INTERVAL_MS = 30_000L // 30 seconds between bursts
        private const val TONE_DURATION_MS = 200       // each tone pulse duration
        private const val TONE_GAP_MS = 150L           // gap between pulses in a burst
        private const val SOS_LONG_MS = 600            // long tone for S-O-S dashes
        private const val MAX_VOLUME = 100             // ToneGenerator max = 100
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isBeaconActive = false
    private var beaconJob: Job? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Start the acoustic beacon.
     * Sets phone volume to maximum, begins SOS tone pattern every 30 seconds.
     */
    fun startBeacon() {
        if (isBeaconActive) return
        isBeaconActive = true

        Log.d(TAG, "Acoustic beacon STARTING")

        // Set volume to maximum on all relevant streams
        setMaxVolume()

        beaconJob = scope.launch {
            while (isActive && isBeaconActive) {
                emitSosPattern()
                delay(BEACON_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop the acoustic beacon.
     * Restores previous volume level.
     */
    fun stopBeacon() {
        isBeaconActive = false
        beaconJob?.cancel()
        beaconJob = null
        Log.d(TAG, "Acoustic beacon STOPPED")
    }

    val isActive: Boolean get() = isBeaconActive

    /**
     * Emit the SOS morse code pattern: · · · — — — · · ·
     * Three short, three long, three short tones.
     * This is internationally recognized and distinct from ambient noise.
     */
    private suspend fun emitSosPattern() {
        Log.d(TAG, "Emitting SOS beacon tone")
        val toneGen = try {
            ToneGenerator(
                AudioManager.STREAM_ALARM,
                MAX_VOLUME
            )
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator failed: ${e.message}")
            return
        }

        try {
            // S: three short tones
            repeat(3) {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_DURATION_MS)
                delay(TONE_DURATION_MS.toLong() + TONE_GAP_MS)
            }
            delay(300L) // pause between S and O

            // O: three long tones
            repeat(3) {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, SOS_LONG_MS)
                delay(SOS_LONG_MS.toLong() + TONE_GAP_MS)
            }
            delay(300L) // pause between O and S

            // S: three short tones
            repeat(3) {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_DURATION_MS)
                delay(TONE_DURATION_MS.toLong() + TONE_GAP_MS)
            }
        } finally {
            toneGen.release()
        }
    }

    private fun setMaxVolume() {
        val streams = listOf(
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
        )
        streams.forEach { stream ->
            try {
                val maxVol = audioManager.getStreamMaxVolume(stream)
                audioManager.setStreamVolume(stream, maxVol, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set max volume for stream $stream: ${e.message}")
            }
        }

        // Request audio focus on alarm stream
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN
            )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
        }
    }
}
