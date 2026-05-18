package omnimesh.command1.location

import android.util.Log
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Converts Bluetooth RSSI readings to distance estimates and
 * uses weighted least-squares triangulation to compute a position.
 *
 * Physics background:
 * RSSI (Received Signal Strength Indicator) follows the log-distance
 * path loss model: RSSI = TxPower - 10 * n * log10(distance)
 * where n is the path loss exponent (2.0 in free space, 3.0-4.0 in buildings).
 *
 * In a collapsed building we use n=3.5 because concrete and debris
 * attenuate signals significantly more than open air.
 */
object RssiTriangulator {

    private const val TAG = "RssiTriangulator"

    // Bluetooth transmission power at 1 meter (dBm).
    // Standard reference value used by Android Nearby Connections.
    private const val TX_POWER_DBM = -59

    // Path loss exponent — 2.0 free space, 3.5 debris/concrete
    private const val PATH_LOSS_EXPONENT_NORMAL = 2.0
    private const val PATH_LOSS_EXPONENT_DEBRIS = 3.5

    // Minimum peers needed for a reliable triangulation fix
    const val MIN_PEERS_FOR_TRIANGULATION = 3

    /**
     * Convert RSSI in dBm to estimated distance in meters.
     * Uses a higher path loss exponent in collapse scenarios to compensate
     * for the heavy attenuation of concrete and debris.
     */
    fun rssiToDistanceMeters(
        rssi: Int,
        isCollapseScenario: Boolean = false,
    ): Double {
        val n = if (isCollapseScenario) PATH_LOSS_EXPONENT_DEBRIS else PATH_LOSS_EXPONENT_NORMAL
        val exponent = (TX_POWER_DBM - rssi) / (10.0 * n)
        val distance = 10.0.pow(exponent)
        return distance.coerceIn(0.5, 100.0)
    }

    /**
     * Weighted least-squares 2D triangulation from N peer observations.
     *
     * Each peer provides:
     *  - Their known GPS position (lat, lon)
     *  - The RSSI we measured from them (converted to distance estimate)
     *
     * Solves the system of circle equations
     *   (x - xi)^2 + (y - yi)^2 = di^2
     * for each peer i, using iterative weighted least squares.
     *
     * Returns null if fewer than MIN_PEERS_FOR_TRIANGULATION observations.
     */
    fun triangulate(
        observations: List<PeerRssiObservation>,
        isCollapseScenario: Boolean = false,
    ): IndoorLocation? {
        if (observations.size < MIN_PEERS_FOR_TRIANGULATION) {
            Log.d(TAG, "Not enough peers for triangulation: ${observations.size}")
            return null
        }

        // Convert GPS coordinates to a local Cartesian frame (meters) relative
        // to the centroid of the observations. Avoids floating point precision
        // problems doing arithmetic on raw degrees of lat/lon.
        val centroidLat = observations.map { it.peerLat }.average()
        val centroidLon = observations.map { it.peerLon }.average()
        val cosLat = cos(Math.toRadians(centroidLat))

        val peers = observations.map { obs ->
            val x = (obs.peerLon - centroidLon) * 111111.0 * cosLat
            val y = (obs.peerLat - centroidLat) * 111111.0
            Triple(x, y, obs.estimatedDistanceMeters)
        }

        var estimateX = 0.0
        var estimateY = 0.0
        val maxIterations = 50
        val convergenceThreshold = 0.01 // 1 cm

        for (iteration in 0 until maxIterations) {
            var numeratorX = 0.0
            var numeratorY = 0.0
            var denominator = 0.0

            for ((px, py, dist) in peers) {
                val dx = estimateX - px
                val dy = estimateY - py
                val currentDist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001)

                // Weight inversely proportional to distance variance — closer
                // peers (stronger RSSI) are more reliable.
                val weight = 1.0 / (dist * dist)

                numeratorX += weight * (px + (estimateX - px) * dist / currentDist)
                numeratorY += weight * (py + (estimateY - py) * dist / currentDist)
                denominator += weight
            }

            if (denominator == 0.0) break

            val newX = numeratorX / denominator
            val newY = numeratorY / denominator

            val delta = sqrt((newX - estimateX).pow(2) + (newY - estimateY).pow(2))
            estimateX = newX
            estimateY = newY

            if (delta < convergenceThreshold) {
                Log.d(TAG, "Triangulation converged at iteration $iteration")
                break
            }
        }

        val resultLat = centroidLat + (estimateY / 111111.0)
        val resultLon = centroidLon + (estimateX / (111111.0 * cosLat))

        val accuracy = estimateAccuracy(observations, isCollapseScenario)

        Log.d(
            TAG,
            "Triangulation result: ($resultLat, $resultLon) " +
                "accuracy=${accuracy}m from ${observations.size} peers",
        )

        return IndoorLocation(
            lat = resultLat,
            lon = resultLon,
            accuracyMeters = accuracy,
            estimatedFloor = null, // caller fills in if barometric data available
            floorConfidence = 0f,
            method = if (observations.size >= MIN_PEERS_FOR_TRIANGULATION) {
                LocationMethod.RSSI_TRIANGULATION
            } else {
                LocationMethod.RSSI_SINGLE_PEER
            },
            contributingPeerCount = observations.size,
        )
    }

    /**
     * Estimate the accuracy of a triangulation result.
     * Combines a base figure with a peer-count bonus and a penalty proportional
     * to the standard deviation of the contributing RSSI readings (a rough
     * proxy for the geometric dilution of precision).
     */
    private fun estimateAccuracy(
        observations: List<PeerRssiObservation>,
        isCollapseScenario: Boolean,
    ): Float {
        // RSSI is typically accurate to within 2-3 m in open air, 5-8 m in
        // debris. We use the conservative end of the debris range as base.
        val baseAccuracy = if (isCollapseScenario) 5.0f else 2.5f

        val peerBonus = when (observations.size) {
            3 -> 1.0f
            4 -> 0.85f
            5 -> 0.75f
            else -> 0.65f
        }

        val rssiValues = observations.map { it.rssi.toDouble() }
        val rssiMean = rssiValues.average()
        val rssiStdDev = sqrt(rssiValues.map { (it - rssiMean).pow(2) }.average())
        val variancePenalty = (rssiStdDev / 10.0).toFloat().coerceIn(1.0f, 2.5f)

        return baseAccuracy * peerBonus * variancePenalty
    }
}
