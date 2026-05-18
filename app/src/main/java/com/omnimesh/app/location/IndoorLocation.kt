package omnimesh.command1.location

/**
 * Represents a computed indoor location estimate.
 * May come from GPS (outdoors), RSSI triangulation (indoors),
 * or barometric floor estimation, or a combination.
 */
data class IndoorLocation(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val estimatedFloor: Int?,           // null if unknown
    val floorConfidence: Float,         // 0.0-1.0
    val method: LocationMethod,
    val contributingPeerCount: Int = 0, // how many peers contributed to RSSI fix
    val timestamp: Long = System.currentTimeMillis(),
)

enum class LocationMethod {
    GPS,                // standard GPS, outdoors
    RSSI_TRIANGULATION, // computed from Bluetooth RSSI of known peers
    RSSI_SINGLE_PEER,   // only one peer available, rough estimate only
    BAROMETRIC_FLOOR,   // floor from pressure, lat/lon from last GPS
    GPS_PLUS_FLOOR,     // GPS lat/lon + barometric floor
    LAST_KNOWN,         // no current fix, using last known position
}

/**
 * A single RSSI observation from one peer device.
 * Stored when a peer reports their GPS location and we measure their signal strength.
 */
data class PeerRssiObservation(
    val peerId: String,
    val peerLat: Double,
    val peerLon: Double,
    val peerFloorEstimate: Int?,
    val rssi: Int,                // dBm, typically -40 (close) to -90 (far)
    val estimatedDistanceMeters: Double,
    val observedAt: Long = System.currentTimeMillis(),
)
