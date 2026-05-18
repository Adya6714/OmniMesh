package omnimesh.command1.data

import androidx.lifecycle.LiveData
import androidx.room.*

// 💡 A DAO is the "remote control" for your database.
// You define what operations you want (insert, query, update)
// and Room generates the actual SQL code automatically.
@Dao
interface PacketQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    // IGNORE = if same packet ID arrives twice via mesh, silently skip it
    // This is how we prevent duplicate packets from flooding the system
    suspend fun insert(packet: TriagePacket)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(packets: List<TriagePacket>)

    // 💡 This is the critical query — RED packets always go first
    // Within same urgency, older packets go first (they've waited longer)
    @Query("""
        SELECT * FROM packets
        WHERE synced = 0
        ORDER BY
            CASE urgency
                WHEN 'RED' THEN 1
                WHEN 'YELLOW' THEN 2
                WHEN 'GREEN' THEN 3
                WHEN 'BLACK' THEN 4
                ELSE 5
            END ASC,
            ts ASC
    """)
    suspend fun getAllByPriority(): List<TriagePacket>

    // LiveData = automatically updates the UI when database changes
    // 💡 This is what powers the live command map —
    // new packet arrives → Room detects it → map refreshes automatically
    @Query("SELECT * FROM packets ORDER BY ts DESC")
    fun observeAll(): LiveData<List<TriagePacket>>

    @Query("SELECT * FROM packets ORDER BY ts DESC")
    suspend fun getAll(): List<TriagePacket>

    @Query("SELECT * FROM packets WHERE urgency = 'RED' AND synced = 0")
    fun observeRedPackets(): LiveData<List<TriagePacket>>

    @Query("SELECT * FROM packets WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 50): List<TriagePacket>

    @Query("UPDATE packets SET synced = 1 WHERE id = :packetId")
    suspend fun markSynced(packetId: String)

    @Query("UPDATE packets SET hopCount = hopCount + 1 WHERE id = :packetId")
    suspend fun incrementHopCount(packetId: String)

    // 💡 Deduplication check — before inserting a relayed mesh packet,
    // check if we already have it. Prevents mesh flooding.
    @Query("SELECT EXISTS(SELECT 1 FROM packets WHERE id = :packetId)")
    suspend fun exists(packetId: String): Boolean

    @Query("SELECT COUNT(*) FROM packets WHERE urgency = :urgency AND synced = 0")
    suspend fun countByUrgency(urgency: String): Int

    @Query("DELETE FROM packets WHERE synced = 1 AND ts < :olderThanTs")
    suspend fun deleteSyncedOlderThan(olderThanTs: Long)

    @Query(
        """UPDATE packets SET
        injury = :injuryText,
        urgency = :urgency,
        signalSources = :signalSources,
        synced = 0
        WHERE id = :packetId"""
    )
    suspend fun updatePacketClinicalData(
        packetId: String,
        injuryText: String,
        urgency: String,
        signalSources: String
    )

    @Query("UPDATE packets SET urgency = :urgency, synced = 0 WHERE id = :packetId")
    suspend fun updatePacketUrgency(packetId: String, urgency: String)

    @Query("SELECT * FROM packets WHERE id = :packetId LIMIT 1")
    suspend fun getPacketById(packetId: String): TriagePacket?
}
