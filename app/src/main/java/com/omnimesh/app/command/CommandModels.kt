package omnimesh.command1.command

import androidx.room.Entity
import androidx.room.PrimaryKey

data class BuddyGroup(
    val id: String,
    val name: String,
    val members: List<BuddyMember>,
    val createdAt: Long = System.currentTimeMillis(),
)

data class BuddyMember(
    val deviceId: String,
    val displayName: String,
    val fcmToken: String,
    val phoneNumber: String?,
    val isCurrentDevice: Boolean = false,
    val lastSeenAt: Long = 0L,
    val lastKnownLat: Double = 0.0,
    val lastKnownLon: Double = 0.0,
)

@Entity(tableName = "timeline_events")
data class TimelineEvent(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val incidentId: String,
    val type: TimelineEventType,
    val title: String,
    val detail: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val urgency: String? = null,
    val deviceId: String? = null,
    val packetId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
)

enum class TimelineEventType {
    INCIDENT_DECLARED,
    FIRST_PACKET,
    RED_PACKET_DETECTED,
    AUTO_SOS_TRIGGERED,
    STRUCTURAL_WARNING,
    GEMINI_ANALYSIS_UPDATED,
    RESPONDER_ENTERED_FIELD,
    SECTOR_CLAIMED,
    SECTOR_CLEARED,
    RESPONDER_REACHED_VICTIM,
    VICTIM_CONFIRMED,
    FALSE_POSITIVE_REPORTED,
    BUDDY_ALERT_SENT,
    PACKET_ESCALATED,
    INCIDENT_CLOSED,
}

data class DataQuality(
    val level: QualityLevel,
    val label: String,
    val detail: String,
    val barsFilled: Int,
    val isConfirmedByResponder: Boolean = false,
    val isFalsePositive: Boolean = false,
)

enum class QualityLevel {
    HIGH,
    MEDIUM,
    LOW,
    CONFIRMED,
    DISPUTED,
}

data class DeploymentUnit(
    val id: String,
    val name: String,
    val organizerName: String,
    val incidentCode: String,
    val deviceCount: Int,
    val activeDeviceCount: Int,
    val lat: Double,
    val lon: Double,
    val isIncidentActive: Boolean = false,
    val createdAt: Long,
)
