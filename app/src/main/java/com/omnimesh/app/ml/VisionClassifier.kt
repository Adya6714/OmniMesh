package com.omnimesh.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import omnimesh.command1.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * VisionClassifier — Signal 1 of the OmniMesh multi-signal fusion pipeline.
 *
 * Uses Gemini 1.5 Flash Vision to classify injury images into triage categories.
 * This replaces the TFLite approach to provide genuine clinical understanding
 * rather than a model trained on scraped web images.
 *
 * Output is a VisionSignal containing a 5-element probability vector:
 * [RED, YELLOW, GREEN, BLACK, STRUCTURAL]
 *
 * Falls back to a neutral low-confidence vector when offline or on API error,
 * so the meta-classifier treats the vision signal as absent rather than wrong.
 */
class VisionClassifier(private val context: Context) {

    companion object {
        private const val TAG = "VisionClassifier"
        private const val MAX_IMAGE_DIMENSION = 512  // pixels — keeps payload small
        private const val JPEG_QUALITY = 85
        private const val TIMEOUT_MS = 8000  // 8 second timeout for inference
        private const val API_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-2.5-flash:generateContent"

        // This prompt is the core of the vision classifier.
        // It forces Gemini to reason like a trained paramedic doing START triage.
        // Temperature 0.0 is set in the API call for deterministic output.
        private val TRIAGE_PROMPT = """
            You are a trained paramedic performing visual triage assessment.
            Analyze this image and classify it according to START triage protocol.
            
            Classification categories:
            - RED: Immediate life threat. Arterial/severe bleeding, unconscious victim, 
              airway compromise, crush injury, severe burn >20% BSA, shock signs.
            - YELLOW: Serious but stable. Fractures, moderate lacerations, 
              burns <20% BSA, walking wounded with significant injury.
            - GREEN: Minor. Small cuts, bruises, abrasions, psychological distress only, 
              ambulatory with no serious injury.
            - BLACK: Deceased or injuries incompatible with survival given available resources.
            - STRUCTURAL: Building collapse, structural damage, rubble — no visible person 
              or the scene itself is the subject (not an injury).
            
            Return ONLY this JSON, no explanation, no markdown:
            {
              "classification": "RED|YELLOW|GREEN|BLACK|STRUCTURAL",
              "confidence": 0.0-1.0,
              "reasoning": "one sentence clinical justification",
              "red_probability": 0.0-1.0,
              "yellow_probability": 0.0-1.0,
              "green_probability": 0.0-1.0,
              "black_probability": 0.0-1.0,
              "structural_probability": 0.0-1.0
            }
            
            Probabilities must sum to 1.0.
            If the image is unclear, blurry, or shows no relevant content,
            set confidence below 0.3 and distribute probabilities evenly.
        """.trimIndent()
    }

    /**
     * Classify an image captured from the camera or loaded from storage.
     *
     * @param imagePath Path to the image file, or null for a neutral (absent) vision signal
     * @return VisionSignal with probability vector and confidence
     */
    suspend fun classify(imagePath: String? = null): VisionSignal =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = loadAndResizeBitmap(imagePath)
                    ?: return@withContext neutralSignal("Could not load image")

                val base64Image = bitmapToBase64(bitmap)
                val response = callGeminiVision(base64Image)

                parseGeminiResponse(response)

            } catch (e: Exception) {
                Log.w(TAG, "Vision classification failed: ${e.message}")
                neutralSignal(e.message ?: "Unknown error")
            }
        }

    /**
     * Load image from file path, resize to MAX_IMAGE_DIMENSION to keep
     * the API payload small and inference fast.
     */
    private fun loadAndResizeBitmap(imagePath: String?): Bitmap? {
        return try {
            val original = if (imagePath != null) {
                BitmapFactory.decodeFile(imagePath)
            } else {
                // No image provided — return null so we use neutral signal
                null
            } ?: return null

            // Resize to fit within MAX_IMAGE_DIMENSION while preserving ratio
            val scale = MAX_IMAGE_DIMENSION.toFloat() /
                    maxOf(original.width, original.height)

            if (scale >= 1.0f) {
                original  // Already small enough
            } else {
                val newWidth = (original.width * scale).toInt()
                val newHeight = (original.height * scale).toInt()
                Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bitmap: ${e.message}")
            null
        }
    }

    /**
     * Convert bitmap to Base64-encoded JPEG string for the Gemini API.
     * JPEG at quality 85 gives good quality at reasonable file size.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Call Gemini 1.5 Flash Vision API with the image and triage prompt.
     * Returns the raw response string from the API.
     */
    private fun callGeminiVision(base64Image: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = URL("$API_ENDPOINT?key=$apiKey")

        // Build the multimodal request — text prompt + image
        val requestBody = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", org.json.JSONArray().apply {
                        // Image part — inline base64
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        // Text prompt part
                        put(JSONObject().apply {
                            put("text", TRIAGE_PROMPT)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.0)
                put("maxOutputTokens", 200)
            })
        }.toString()

        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText()
                Log.w(TAG, "Gemini API error ${connection.responseCode}: $error")
                ""
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse the Gemini API response into a VisionSignal.
     *
     * Gemini returns a nested JSON structure. The actual content is at:
     * candidates[0].content.parts[0].text
     *
     * That text is itself JSON matching our schema.
     */
    private fun parseGeminiResponse(response: String): VisionSignal {
        if (response.isEmpty()) return neutralSignal("Empty API response")

        return try {
            // Navigate the Gemini response structure
            val outerJson = JSONObject(response)
            val candidates = outerJson.getJSONArray("candidates")
            val content = candidates
                .getJSONObject(0)
                .getJSONObject("content")
            val parts = content.getJSONArray("parts")
            var text = parts.getJSONObject(0).getString("text")

            // Strip markdown code fences if present
            text = text
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // Parse the inner triage JSON
            val triageJson = JSONObject(text)

            val classification = triageJson.getString("classification")
            val confidence = triageJson.getDouble("confidence").toFloat()

            val probabilities = floatArrayOf(
                triageJson.optDouble("red_probability", 0.0).toFloat(),
                triageJson.optDouble("yellow_probability", 0.0).toFloat(),
                triageJson.optDouble("green_probability", 0.0).toFloat(),
                triageJson.optDouble("black_probability", 0.0).toFloat(),
                triageJson.optDouble("structural_probability", 0.0).toFloat(),
            )

            val reasoning = triageJson.optString("reasoning", "")

            Log.d(TAG, "Vision: $classification (${"%.2f".format(confidence)}) — $reasoning")

            VisionSignal(
                classification = classification,
                confidence = confidence,
                probabilities = probabilities,
                reasoning = reasoning,
                isOnline = true
            )

        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gemini response: ${e.message}")
            Log.d(TAG, "Raw response: $response")
            neutralSignal("Parse error: ${e.message}")
        }
    }

    /**
     * Returns a neutral low-confidence signal used when:
     * - Device is offline
     * - API call fails
     * - Image cannot be loaded
     * - Response cannot be parsed
     *
     * The meta-classifier is trained to treat low-confidence vision signals
     * as absent — they neither confirm nor contradict the other signals.
     * This is the correct graceful degradation behavior.
     */
    private fun neutralSignal(reason: String): VisionSignal {
        Log.d(TAG, "Using neutral vision signal: $reason")
        return VisionSignal(
            classification = "UNKNOWN",
            confidence = 0.15f,
            // Slightly elevated GREEN probability as a safe default
            // — unknown visual is more likely minor than critical
            probabilities = floatArrayOf(0.15f, 0.20f, 0.35f, 0.10f, 0.20f),
            reasoning = "Offline or unavailable: $reason",
            isOnline = false
        )
    }

    fun close() {
        // No resources to release — no TFLite interpreter
    }
}

/**
 * Output of the vision classification pipeline.
 *
 * @param classification Primary class: RED/YELLOW/GREEN/BLACK/STRUCTURAL/UNKNOWN
 * @param confidence Overall confidence in the classification (0.0-1.0)
 * @param probabilities 5-element array [RED, YELLOW, GREEN, BLACK, STRUCTURAL]
 * @param reasoning One-sentence clinical justification from Gemini
 * @param isOnline Whether this came from a live API call (true) or fallback (false)
 */
data class VisionSignal(
    val classification: String,
    val confidence: Float,
    val probabilities: FloatArray,
    val reasoning: String,
    val isOnline: Boolean
) {
    /**
     * Convert to the probability vector format expected by the meta-classifier.
     * Returns a copy so the original is not mutated.
     */
    fun toProbabilityVector(): FloatArray = probabilities.copyOf()

    /**
     * Human-readable signal source string for the TriagePacket.
     * "V" = Vision signal contributed to this triage decision.
     */
    val signalCode: String get() = if (isOnline && confidence > 0.4f) "V" else ""
}