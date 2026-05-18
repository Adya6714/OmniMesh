package omnimesh.command1.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import omnimesh.command1.command.TimelineEvent
import omnimesh.command1.responder.BreadcrumbWaypoint
import omnimesh.command1.responder.DangerZone
import omnimesh.command1.responder.SectorClaim

// 💡 @Database ties everything together. Whenever the schema changes,
// increment the version and provide a migration strategy.
@Database(
    entities = [
        TriagePacket::class,
        TimelineEvent::class,
        BreadcrumbWaypoint::class,
        SectorClaim::class,
        DangerZone::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class OmniMeshDatabase : RoomDatabase() {

    abstract fun packetDao(): PacketQueueDao
    abstract fun responderDao(): ResponderDao
    abstract fun timelineDao(): TimelineDao

    companion object {
        // 💡 @Volatile means this variable is always read from main memory,
        // never from a CPU cache. Critical for thread safety.
        @Volatile
        private var INSTANCE: OmniMeshDatabase? = null

        fun getInstance(context: Context): OmniMeshDatabase {
            // 💡 synchronized = only one thread can run this block at a time.
            // Prevents two threads from creating two separate databases.
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    OmniMeshDatabase::class.java,
                    "omnimesh_db"
                )
                .fallbackToDestructiveMigration() // dev only — wipe on schema change
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
