package omnimesh.command1.data

import android.content.Context
import android.util.Log
import omnimesh.command1.OmniMeshApp

private const val TAG = "SOS_DEBUG"

// 💡 A Repository sits between your UI/services and the database.
// Nothing outside the data/ package talks to the DAO directly.
// This pattern means if you ever swap Room for a different database,
// you only change ONE file — not every file that touches data.
class PacketRepository(context: Context) {

    private val appContext = context.applicationContext
    private val app = appContext as? OmniMeshApp

    private val db = OmniMeshDatabase.getInstance(appContext)
    private val dao = db.packetDao()

    suspend fun save(packet: TriagePacket) {
        dao.insert(packet)
        Log.d(TAG, "Packet queued locally id=${packet.id} urgency=${packet.urgency}")
        requestSync("save:${packet.id}")
    }

    suspend fun saveAll(packets: List<TriagePacket>) {
        dao.insertAll(packets)
        if (packets.isNotEmpty()) {
            Log.d(TAG, "Queued ${packets.size} packet(s) locally for sync")
            requestSync("saveAll:${packets.size}")
        }
    }

    suspend fun isAlreadyReceived(packetId: String): Boolean {
        return dao.exists(packetId)
    }

    suspend fun getTransmitQueue(): List<TriagePacket> {
        return dao.getAllByPriority()
    }

    suspend fun markSynced(packetId: String) {
        dao.markSynced(packetId)
    }

    suspend fun getUnsyncedPackets(): List<TriagePacket> {
        return dao.getUnsynced()
    }

    suspend fun getAllPackets(): List<TriagePacket> {
        return dao.getAll()
    }

    fun observeAllPackets() = dao.observeAll()
    fun observeRedPackets() = dao.observeRedPackets()

    suspend fun urgencyCounts(): Map<String, Int> = mapOf(
        "RED" to dao.countByUrgency("RED"),
        "YELLOW" to dao.countByUrgency("YELLOW"),
        "GREEN" to dao.countByUrgency("GREEN"),
        "BLACK" to dao.countByUrgency("BLACK")
    )

    // 💡 Housekeeping — delete old synced packets so the DB doesn't grow forever
    // Call this periodically (e.g. every 6 hours via WorkManager)
    suspend fun cleanupSynced(olderThanHours: Int = 24) {
        val cutoff = System.currentTimeMillis() - (olderThanHours * 3600 * 1000L)
        dao.deleteSyncedOlderThan(cutoff)
    }

    suspend fun updatePacketFromCompanion(
        packetId: String,
        injuryText: String,
        urgency: String,
        signalSources: String
    ) {
        dao.updatePacketClinicalData(packetId, injuryText, urgency, signalSources)
        Log.d(TAG, "Packet updated from companion id=$packetId urgency=$urgency")
        requestSync("companion:$packetId")
    }

    suspend fun updatePacketUrgency(packetId: String, urgency: String) {
        dao.updatePacketUrgency(packetId, urgency)
        Log.d(TAG, "Packet urgency updated id=$packetId urgency=$urgency")
        requestSync("urgency:$packetId")
    }

    suspend fun getPacketById(packetId: String): TriagePacket? {
        return dao.getPacketById(packetId)
    }

    private fun requestSync(reason: String) {
        SyncWorker.runNow(appContext)
        app?.requestImmediateFirebaseSync(reason)
    }
}
