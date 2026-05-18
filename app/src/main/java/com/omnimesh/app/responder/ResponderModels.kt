package omnimesh.command1.responder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "breadcrumb_waypoints")
data class BreadcrumbWaypoint(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val responderId: String,
    val incidentId: String,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
)

@Entity(tableName = "sector_claims")
data class SectorClaim(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val incidentId: String,
    val responderId: String,
    val responderName: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Float = 30f,
    val label: String,
    val status: SectorStatus = SectorStatus.SEARCHING,
    val claimedAt: Long = System.currentTimeMillis(),
    val clearedAt: Long? = null,
    val synced: Boolean = false,
)

enum class SectorStatus {
    SEARCHING,
    CLEARED,
    DANGEROUS,
}

@Entity(tableName = "danger_zones")
data class DangerZone(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val incidentId: String,
    val detectedByDeviceId: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Float = 25f,
    val audioConfidence: Float,
    val detectedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val synced: Boolean = false,
)

data class TriageCard(
    val packetId: String,
    val urgency: String,
    val injuryText: String,
    val foundAt: Long,
    val foundByDeviceId: String,
    val treatments: List<String> = emptyList(),
    val notes: String = "",
    val version: Int = 1,
) {
    fun toQrPayload(): String {
        return """{"id":"$packetId","u":"$urgency","t":$foundAt,"v":$version}"""
    }

    companion object {
        fun fromQrPayload(payload: String): TriageCard? {
            return try {
                val json = org.json.JSONObject(payload)
                TriageCard(
                    packetId = json.getString("id"),
                    urgency = json.getString("u"),
                    injuryText = "",
                    foundAt = json.getLong("t"),
                    foundByDeviceId = "",
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class AudioChunk(
    val senderId: String,
    val senderName: String,
    val sequenceNumber: Int,
    val audioData: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toBytes(): ByteArray {
        val header = "$senderId|$senderName|$sequenceNumber|$timestamp|".toByteArray()
        return header + audioData
    }

    companion object {
        fun fromBytes(bytes: ByteArray): AudioChunk? {
            return try {
                val separatorIndices = mutableListOf<Int>()
                var pipeCount = 0
                var i = 0
                while (i < bytes.size && pipeCount < 4) {
                    if (bytes[i] == '|'.code.toByte()) {
                        separatorIndices.add(i)
                        pipeCount++
                    }
                    i++
                }
                if (separatorIndices.size < 4) return null
                val headerEnd = separatorIndices[3] + 1
                val header = String(bytes.slice(0 until separatorIndices[3]).toByteArray())
                val parts = header.split("|")
                val audioData = bytes.sliceArray(headerEnd until bytes.size)
                AudioChunk(
                    senderId = parts[0],
                    senderName = parts[1],
                    sequenceNumber = parts[2].toInt(),
                    audioData = audioData,
                    timestamp = parts[3].toLong(),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
