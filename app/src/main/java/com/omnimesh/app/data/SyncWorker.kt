package omnimesh.command1.data

import android.content.Context
import android.util.Log
import androidx.work.*
import omnimesh.command1.OmniMeshApp
import java.util.concurrent.TimeUnit

private const val TAG = "SyncWorker"
private const val SYNC_LOG_TAG = "FirebaseSync"

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started")
        Log.d(SYNC_LOG_TAG, "SyncWorker executing with network constraints satisfied")

        return try {
            val repository = (applicationContext as OmniMeshApp).repository
            val syncManager = FirebaseSyncManager(repository)
            val result = syncManager.syncPendingPackets()

            if (result.error != null) {
                Log.w(TAG, "Sync had errors — retrying later")
                // 💡 Result.retry() tells WorkManager to try again later
                // using exponential backoff. Not Result.failure() which gives up.
                Result.retry()
            } else {
                Log.d(TAG, "Sync succeeded: ${result.uploaded} uploaded")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker crashed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "omnimesh_firebase_sync"
        private const val IMMEDIATE_WORK_NAME = "omnimesh_firebase_sync_now"

        fun schedule(context: Context) {
            // 💡 Constraints: only run when network is available.
            // WorkManager will queue the job and run it the moment
            // connectivity returns — even hours later.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Periodic sync every 15 minutes when online
            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // 💡 KEEP = if already scheduled, don't replace it.
                // Prevents duplicate sync jobs if service restarts.
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d(SYNC_LOG_TAG, "Periodic Firebase sync scheduled")
        }

        // Trigger immediate sync (call when internet returns)
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    oneTimeRequest
                )

            Log.d(SYNC_LOG_TAG, "Immediate Firebase sync job queued")
        }
    }
}
