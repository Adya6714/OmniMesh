package omnimesh.command1.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import omnimesh.command1.responder.BreadcrumbWaypoint
import omnimesh.command1.responder.DangerZone
import omnimesh.command1.responder.SectorClaim
import omnimesh.command1.responder.SectorStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ResponderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWaypoint(waypoint: BreadcrumbWaypoint)

    @Query("SELECT * FROM breadcrumb_waypoints WHERE incidentId = :incidentId ORDER BY timestamp ASC")
    fun observeWaypoints(incidentId: String): Flow<List<BreadcrumbWaypoint>>

    @Query("SELECT * FROM breadcrumb_waypoints WHERE responderId = :responderId AND incidentId = :incidentId ORDER BY timestamp ASC")
    suspend fun getWaypointsForResponder(responderId: String, incidentId: String): List<BreadcrumbWaypoint>

    @Query("SELECT * FROM breadcrumb_waypoints WHERE synced = 0")
    suspend fun getUnsyncedWaypoints(): List<BreadcrumbWaypoint>

    @Query("UPDATE breadcrumb_waypoints SET synced = 1 WHERE id IN (:ids)")
    suspend fun markWaypointsSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectorClaim(claim: SectorClaim)

    @Query("SELECT * FROM sector_claims WHERE incidentId = :incidentId")
    fun observeSectorClaims(incidentId: String): Flow<List<SectorClaim>>

    @Query("UPDATE sector_claims SET status = :status, clearedAt = :clearedAt, synced = 0 WHERE id = :id")
    suspend fun updateSectorStatus(id: String, status: SectorStatus, clearedAt: Long?)

    @Query("SELECT * FROM sector_claims WHERE synced = 0")
    suspend fun getUnsyncedClaims(): List<SectorClaim>

    @Query("UPDATE sector_claims SET synced = 1 WHERE id IN (:ids)")
    suspend fun markClaimsSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDangerZone(zone: DangerZone)

    @Query("SELECT * FROM danger_zones WHERE incidentId = :incidentId AND isActive = 1")
    fun observeActiveDangerZones(incidentId: String): Flow<List<DangerZone>>

    @Query("UPDATE danger_zones SET isActive = 0 WHERE id = :id")
    suspend fun deactivateDangerZone(id: String)

    @Query("SELECT * FROM danger_zones WHERE synced = 0")
    suspend fun getUnsyncedDangerZones(): List<DangerZone>

    @Query("UPDATE danger_zones SET synced = 1 WHERE id IN (:ids)")
    suspend fun markDangerZonesSynced(ids: List<String>)
}
