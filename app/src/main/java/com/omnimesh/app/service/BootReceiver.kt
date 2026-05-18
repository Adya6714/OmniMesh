package omnimesh.command1.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import omnimesh.command1.data.SyncWorker

private const val TAG = "FirebaseSync"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 💡 After phone restarts during a disaster, the mesh
            // automatically rejoins without the user doing anything.
            SyncWorker.schedule(context)
            SyncWorker.runNow(context)
            Log.d(TAG, "Boot completed — Firebase sync rescheduled")
            val serviceIntent = Intent(context, MeshRelayService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
