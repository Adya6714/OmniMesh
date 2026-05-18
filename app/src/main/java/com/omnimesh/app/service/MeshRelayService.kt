package omnimesh.command1.service
import android.app.*
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import omnimesh.command1.OmniMeshApp
import omnimesh.command1.mesh.NearbyMeshManager
import omnimesh.command1.mesh.MeshStatus
import kotlinx.coroutines.*

// 💡 A Foreground Service keeps running even when the user
// switches to another app. Android requires a visible notification
// for this — that's the "OmniMesh Active" notification you'll see.
// Without foreground service, Android kills our mesh after ~1 minute.
class MeshRelayService : Service() {

    private lateinit var meshManager: NearbyMeshManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "omnimesh_mesh_channel"
        const val NOTIFICATION_ID = 1001

        // How often we push our queue to connected peers
        private const val RELAY_INTERVAL_MS = 5000L  // every 5 seconds
    }

    override fun onCreate() {
        super.onCreate()
        meshManager = (application as OmniMeshApp).nearbyMeshManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Searching for mesh nodes..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Searching for mesh nodes...")
            )
        }
        meshManager.start()

        // 💡 Periodic relay loop — every 5 seconds, push our priority
        // queue to all connected peers. This ensures new peers that
        // just connected get packets they missed.
        scope.launch {
            while (isActive) {
                meshManager.broadcastPriorityQueue()

                // Update notification with peer count
                val peerCount = meshManager.connectedEndpoints.value.size
                updateNotification("Connected to $peerCount mesh node(s)")

                delay(RELAY_INTERVAL_MS)
            }
        }

        // START_STICKY = if Android kills this service, restart it automatically
        return START_STICKY
    }

    private fun buildNotification(status: String): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniMesh Active")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)       // Can't be dismissed by user
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OmniMesh Mesh Network",
            NotificationManager.IMPORTANCE_LOW  // Silent — no sound/vibration
        ).apply { description = "Keeps the mesh relay running in background" }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        meshManager.stop()
        scope.cancel()
        super.onDestroy()
    }
}
