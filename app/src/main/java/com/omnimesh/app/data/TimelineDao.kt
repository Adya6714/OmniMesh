package omnimesh.command1.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import omnimesh.command1.command.TimelineEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: TimelineEvent)

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE incidentId = :incidentId
        ORDER BY timestamp DESC
    """
    )
    fun observeEvents(incidentId: String): Flow<List<TimelineEvent>>

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE incidentId = :incidentId
        ORDER BY timestamp DESC
        LIMIT :limit
    """
    )
    suspend fun getRecentEvents(incidentId: String, limit: Int = 50): List<TimelineEvent>

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE incidentId = :incidentId
        AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC
    """
    )
    suspend fun getEventsInWindow(
        incidentId: String,
        from: Long,
        to: Long
    ): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE synced = 0")
    suspend fun getUnsyncedEvents(): List<TimelineEvent>

    @Query("UPDATE timeline_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query(
        """
        SELECT COUNT(*) FROM timeline_events
        WHERE incidentId = :incidentId
        AND type IN ('RED_PACKET_DETECTED', 'AUTO_SOS_TRIGGERED')
        AND timestamp BETWEEN :from AND :to
    """
    )
    suspend fun countRedEventsInWindow(
        incidentId: String,
        from: Long,
        to: Long
    ): Int
}
