package omnimesh.command1

import android.app.Application
import android.util.Log
import omnimesh.command1.command.BuddyGroupManager
import omnimesh.command1.command.IncidentTimelineManager
import omnimesh.command1.companion.CompanionSessionManager
import omnimesh.command1.data.FirebaseSyncManager
import omnimesh.command1.data.OmniMeshDatabase
import omnimesh.command1.data.PacketRepository
import omnimesh.command1.data.SyncWorker
import omnimesh.command1.disaster.DisasterStateManager
import omnimesh.command1.location.IndoorLocationManager
import omnimesh.command1.mesh.NearbyMeshManager
import omnimesh.command1.mesh.WalkieTalkieManager
import omnimesh.command1.ml.DispatchAgent
import omnimesh.command1.responder.BreadcrumbTrailManager
import omnimesh.command1.responder.QrTriageCardManager
import omnimesh.command1.responder.StructuralDangerDetector
import omnimesh.command1.service.AcousticBeaconService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val APP_SYNC_TAG = "FirebaseSync"

class OmniMeshApp : Application() {

    // 💡 "by lazy" means these are only created when first accessed,
    // not when the app starts. Saves startup time.
    val database by lazy { OmniMeshDatabase.getInstance(this) }
    val repository by lazy { PacketRepository(this) }
    val firebaseSyncManager by lazy { FirebaseSyncManager(repository) }
    val dispatchAgent by lazy { DispatchAgent(BuildConfig.GEMINI_API_KEY) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncNowJob: Job? = null

    val indoorLocationManager: IndoorLocationManager by lazy { IndoorLocationManager(this) }

    val acousticBeacon: AcousticBeaconService by lazy { AcousticBeaconService(this) }
    val disasterStateManager: DisasterStateManager by lazy { DisasterStateManager() }
    val companionSessionManager: CompanionSessionManager by lazy {
        CompanionSessionManager(this, repository)
    }
    val nearbyMeshManager: NearbyMeshManager by lazy {
        NearbyMeshManager(
            context = this,
            repository = repository,
            onPacketReceived = {}
        )
    }
    val breadcrumbTrailManager: BreadcrumbTrailManager by lazy {
        BreadcrumbTrailManager(this)
    }
    val structuralDangerDetector: StructuralDangerDetector by lazy {
        StructuralDangerDetector(this)
    }
    val qrTriageCardManager: QrTriageCardManager by lazy {
        QrTriageCardManager(repository)
    }
    val walkieTalkieManager: WalkieTalkieManager by lazy {
        WalkieTalkieManager(this, nearbyMeshManager).also {
            nearbyMeshManager.walkieTalkieManager = it
        }
    }
    val timelineManager: IncidentTimelineManager by lazy {
        IncidentTimelineManager(this)
    }
    val buddyGroupManager: BuddyGroupManager by lazy {
        BuddyGroupManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
        SyncWorker.runNow(this)
        appScope.launch {
            try {
                firebaseSyncManager.ensureAuthenticated()
                Log.d(APP_SYNC_TAG, "Startup anonymous auth ensured")
                buddyGroupManager.registerDevice(displayName = "OmniMesh User")
            } catch (e: Exception) {
                Log.e(APP_SYNC_TAG, "Startup auth bootstrap failed", e)
            }
        }
        indoorLocationManager.start()
        companionSessionManager.init()
    }

    fun requestImmediateFirebaseSync(reason: String) {
        if (syncNowJob?.isActive == true) {
            Log.d(APP_SYNC_TAG, "Immediate sync already running — latest reason=$reason")
            return
        }
        syncNowJob = appScope.launch {
            try {
                Log.d(APP_SYNC_TAG, "Immediate sync requested reason=$reason")
                val result = firebaseSyncManager.syncPendingPackets()
                Log.d(APP_SYNC_TAG, "Immediate sync finished uploaded=${result.uploaded} failed=${result.failed}")
            } catch (e: Exception) {
                Log.e(APP_SYNC_TAG, "Immediate sync crashed reason=$reason", e)
            }
        }
    }
}
