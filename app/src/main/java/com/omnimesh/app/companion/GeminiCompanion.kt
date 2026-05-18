package omnimesh.command1.companion

import android.util.Log
import omnimesh.command1.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Gemini-powered disaster companion.
 */
class GeminiCompanion {

    companion object {
        private const val TAG = "GeminiCompanion"
        private const val API_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-2.5-flash:generateContent"
        private const val MAX_TOKENS = 300
        private const val TEMPERATURE = 0.3
    }

    private val conversationHistory = mutableListOf<CompanionMessage>()

    fun buildSystemPrompt(
        incidentType: String,
        victimLat: Double,
        victimLon: Double,
        floorEstimate: Int?,
        minutesSinceAlert: Int,
        clinicalState: VictimClinicalState,
        responderCount: Int,
    ): String = """
You are the OmniMesh Disaster Companion — a calm, reassuring AI assistant 
helping a person who may be trapped or injured in a disaster.

MISSION: Keep this person alive, calm, and informed until rescue teams arrive.
You do this by: assessing their condition, providing first aid guidance,
offering psychological support, and giving periodic rescue timeline updates.

CURRENT SITUATION:
- Disaster type: $incidentType
- Victim location: ($victimLat, $victimLon)${floorEstimate?.let { ", Floor $it" } ?: ""}
- Time since alert sent: $minutesSinceAlert minutes
- Rescue teams notified: ${if (responderCount > 0) "YES — $responderCount responders active" else "YES — teams being dispatched"}

KNOWN CLINICAL STATE:
${if (clinicalState.reportedInjuries.isEmpty()) "- No injuries reported yet" else "- Reported injuries: ${clinicalState.reportedInjuries.joinToString(", ")}"}
${clinicalState.breathingDifficulty?.let { "- Breathing difficulty: $it" } ?: ""}
${clinicalState.isTrapped?.let { "- Trapped: $it" } ?: "- Trapped status unknown"}
${clinicalState.canMove?.let { "- Can move: $it" } ?: ""}
${clinicalState.painLevel?.let { "- Pain level: $it/10" } ?: ""}

CLINICAL ASSESSMENT PROTOCOL:
In your FIRST response, introduce yourself and ask about breathing.

GUIDANCE RULES:
- NEVER give advice that could worsen injuries
- For crush injuries: keep still, do not remove trapping object without medical help
- For breathing difficulty: sit upright if possible, stay calm
- For bleeding: apply direct pressure, do not remove dressing
- For flooding: move up, never down
- Update victim on rescue progress every 3-4 exchanges

COMMUNICATION STYLE:
- Short sentences
- Calm and reassuring
- End most responses with a gentle question or task
- Maximum 3 sentences per response

CRITICAL: If breathing stops, victim becomes unresponsive, or severe chest pain, include ESCALATE_URGENCY.
Respond only with spoken message text.
    """.trimIndent()

    suspend fun sendMessage(
        userMessage: String,
        systemPrompt: String,
    ): CompanionResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        conversationHistory.add(CompanionMessage(role = MessageRole.VICTIM, text = userMessage))

        val messagesArray = JSONArray()
        conversationHistory.forEach { msg ->
            val role = when (msg.role) {
                MessageRole.VICTIM -> "user"
                MessageRole.COMPANION -> "model"
                MessageRole.SYSTEM -> "user"
            }
            messagesArray.put(
                JSONObject().apply {
                    put("role", role)
                    put(
                        "parts",
                        JSONArray().apply { put(JSONObject().apply { put("text", msg.text) }) }
                    )
                }
            )
        }

        val requestBody = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().apply {
                    put(
                        "parts",
                        JSONArray().apply { put(JSONObject().apply { put("text", systemPrompt) }) }
                    )
                }
            )
            put("contents", messagesArray)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", TEMPERATURE)
                    put("maxOutputTokens", MAX_TOKENS)
                }
            )
        }.toString()

        try {
            val url = URL("$API_ENDPOINT?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.outputStream.use { it.write(requestBody.toByteArray()) }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val text = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                conversationHistory.add(CompanionMessage(role = MessageRole.COMPANION, text = text))
                val shouldEscalate = text.contains("ESCALATE_URGENCY", ignoreCase = true)
                val cleanText = text.replace("ESCALATE_URGENCY", "").trim()

                Log.d(TAG, "Companion response: $cleanText")
                CompanionResponse(cleanText, shouldEscalate, success = true)
            } else {
                Log.w(TAG, "Gemini API error: ${conn.responseCode}")
                CompanionResponse(
                    text = "I'm having trouble connecting. Your alert has been sent. Stay calm.",
                    shouldEscalate = false,
                    success = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Companion API call failed: ${e.message}")
            CompanionResponse(
                text = "Stay calm. Your SOS alert has been transmitted. Help is on the way.",
                shouldEscalate = false,
                success = false
            )
        }
    }

    suspend fun extractClinicalState(systemPrompt: String): VictimClinicalState =
        withContext(Dispatchers.IO) {
            val extractionPrompt = """
Based on this conversation, extract clinical information as JSON only.
{
  "can_breathe": true/false/null,
  "breathing_difficulty": true/false/null,
  "can_move": true/false/null,
  "is_trapped": true/false/null,
  "injuries": ["list"],
  "pain_level": 0-10 or null,
  "floor_location": "description or null",
  "survivors_count": number
}
            """.trimIndent()

            val historyText = conversationHistory
                .filter { it.role != MessageRole.SYSTEM }
                .joinToString("\n") { "${it.role.name}: ${it.text}" }
            val apiKey = BuildConfig.GEMINI_API_KEY

            val requestBody = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put(
                                    "parts",
                                    JSONArray().apply {
                                        put(
                                            JSONObject().apply {
                                                put(
                                                    "text",
                                                    "CONVERSATION:\n$historyText\n\n$extractionPrompt\n$systemPrompt"
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.0)
                        put("maxOutputTokens", 220)
                    }
                )
            }.toString()

            try {
                val url = URL("$API_ENDPOINT?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.outputStream.use { it.write(requestBody.toByteArray()) }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText)
                    val text = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()

                    val extracted = JSONObject(text)
                    val injuries = mutableListOf<String>()
                    val injuriesArray = extracted.optJSONArray("injuries")
                    if (injuriesArray != null) {
                        for (i in 0 until injuriesArray.length()) injuries.add(injuriesArray.getString(i))
                    }

                    VictimClinicalState(
                        canBreathe = if (extracted.isNull("can_breathe")) null else extracted.optBoolean("can_breathe"),
                        breathingDifficulty = if (extracted.isNull("breathing_difficulty")) null else extracted.optBoolean("breathing_difficulty"),
                        canMove = if (extracted.isNull("can_move")) null else extracted.optBoolean("can_move"),
                        isTrapped = if (extracted.isNull("is_trapped")) null else extracted.optBoolean("is_trapped"),
                        reportedInjuries = injuries,
                        painLevel = if (extracted.isNull("pain_level")) null else extracted.optInt("pain_level"),
                        location = extracted.optString("floor_location").takeIf { it.isNotBlank() && it != "null" },
                        numberOfSurvivors = extracted.optInt("survivors_count", 1),
                    )
                } else {
                    VictimClinicalState()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Clinical extraction failed: ${e.message}")
                VictimClinicalState()
            }
        }

    fun clearHistory() = conversationHistory.clear()
    fun getHistory(): List<CompanionMessage> = conversationHistory.toList()
}

data class CompanionResponse(
    val text: String,
    val shouldEscalate: Boolean,
    val success: Boolean,
)
