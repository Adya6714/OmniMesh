package omnimesh.command1.companion

/**
 * A single message in the companion conversation.
 * Can be from the AI companion or from the victim (via voice or text).
 */
data class CompanionMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSpoken: Boolean = false, // whether TTS has spoken this message
    val updatesTriagePacket: Boolean = false, // whether this triggered a packet update
)

enum class MessageRole { COMPANION, VICTIM, SYSTEM }

/**
 * Clinical information extracted from the companion conversation.
 * This updates the TriagePacket as more information is gathered.
 */
data class VictimClinicalState(
    val canBreathe: Boolean? = null,
    val breathingDifficulty: Boolean? = null,
    val isConscious: Boolean = true,
    val canMove: Boolean? = null,
    val isTrapped: Boolean? = null,
    val reportedInjuries: List<String> = emptyList(),
    val painLevel: Int? = null, // 0-10 scale from conversation
    val location: String? = null, // "third floor, stairwell B"
    val numberOfSurvivors: Int = 1, // may report others nearby
    val derivedUrgency: String? = null, // RED/YELLOW/GREEN derived from conversation
    val lastUpdated: Long = System.currentTimeMillis(),
) {
    fun toInjuryString(): String {
        val parts = mutableListOf<String>()
        if (reportedInjuries.isNotEmpty()) parts.add(reportedInjuries.joinToString(", "))
        breathingDifficulty?.let { if (it) parts.add("breathing difficulty") }
        canMove?.let { if (!it) parts.add("cannot move") }
        isTrapped?.let { if (it) parts.add("TRAPPED") }
        painLevel?.let { if (it >= 7) parts.add("severe pain ($it/10)") }
        return parts.joinToString("; ").ifEmpty { "Victim in distress" }
    }

    fun computeUrgency(): String = when {
        breathingDifficulty == true -> "RED"
        isTrapped == true && canBreathe == false -> "RED"
        isTrapped == true -> "RED"
        painLevel != null && painLevel >= 8 -> "RED"
        canMove == false -> "YELLOW"
        reportedInjuries.any {
            it.contains("bleed", true) ||
                it.contains("fracture", true) ||
                it.contains("broken", true)
        } -> "YELLOW"
        else -> "GREEN"
    }
}

/**
 * State machine for the companion session.
 */
enum class CompanionState {
    IDLE, // not active
    ACTIVATING, // just started, playing intro message
    ASSESSING, // gathering clinical information
    SUPPORTING, // providing ongoing support and periodic updates
    BRIDGING, // connecting victim to a nearby responder
    ENDED, // session ended
}

/**
 * START triage self-assessment result.
 */
data class StartTriageResult(
    val canWalk: Boolean? = null,
    val respirationsPerMinute: Int? = null,
    val radialPulsePresent: Boolean? = null,
    val canFollowCommands: Boolean? = null,
    val derivedCategory: StartCategory = StartCategory.UNKNOWN,
)

enum class StartCategory(val color: String, val description: String) {
    GREEN("GREEN", "Minor — Walking wounded, self-ambulatory"),
    YELLOW("YELLOW", "Delayed — Serious but stable, can wait"),
    RED("RED", "Immediate — Life threatening, needs urgent care"),
    BLACK("BLACK", "Expectant — Deceased or unsurvivable injuries"),
    UNKNOWN("UNKNOWN", "Assessment incomplete"),
}
