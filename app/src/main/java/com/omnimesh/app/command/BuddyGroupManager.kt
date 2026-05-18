package omnimesh.command1.command

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import omnimesh.command1.utils.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BuddyGroupManager(private val context: Context) {

    companion object {
        private const val TAG = "BuddyGroup"
        private const val COLLECTION_GROUPS = "buddy_groups"
        private const val COLLECTION_DEVICES = "registered_devices"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val deviceId = DeviceUtils.getDeviceId(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _myGroup = MutableStateFlow<BuddyGroup?>(null)
    val myGroup: StateFlow<BuddyGroup?> = _myGroup.asStateFlow()

    private val _memberLocations = MutableStateFlow<Map<String, Pair<Double, Double>>>(emptyMap())
    val memberLocations: StateFlow<Map<String, Pair<Double, Double>>> = _memberLocations.asStateFlow()

    fun registerDevice(displayName: String, phoneNumber: String? = null) {
        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val deviceData = mapOf(
                    "deviceId" to deviceId,
                    "displayName" to displayName,
                    "fcmToken" to token,
                    "phoneNumber" to (phoneNumber ?: ""),
                    "lastSeenAt" to System.currentTimeMillis(),
                )
                firestore.collection(COLLECTION_DEVICES).document(deviceId).set(deviceData).await()
                Log.d(TAG, "Device registered: $deviceId")
            } catch (e: Exception) {
                Log.w(TAG, "Device registration failed: ${e.message}")
            }
        }
    }

    suspend fun createGroup(groupName: String, displayName: String): String? {
        return try {
            val joinCode = generateJoinCode()
            val token = FirebaseMessaging.getInstance().token.await()
            val groupData = mapOf(
                "id" to joinCode,
                "name" to groupName,
                "createdAt" to System.currentTimeMillis(),
                "members" to listOf(
                    mapOf(
                        "deviceId" to deviceId,
                        "displayName" to displayName,
                        "fcmToken" to token,
                        "isOwner" to true,
                    )
                )
            )
            firestore.collection(COLLECTION_GROUPS).document(joinCode).set(groupData).await()
            loadGroup(joinCode)
            Log.d(TAG, "Buddy group created: $joinCode")
            joinCode
        } catch (e: Exception) {
            Log.w(TAG, "Create group failed: ${e.message}")
            null
        }
    }

    suspend fun joinGroup(joinCode: String, displayName: String): Boolean {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            val newMember = mapOf(
                "deviceId" to deviceId,
                "displayName" to displayName,
                "fcmToken" to token,
                "isOwner" to false,
            )
            val docRef = firestore.collection(COLLECTION_GROUPS).document(joinCode)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val members = snapshot.get("members") as? List<*> ?: emptyList<Any>()
                val updatedMembers = members.toMutableList().also { it.add(newMember) }
                transaction.update(docRef, "members", updatedMembers)
            }.await()
            loadGroup(joinCode)
            Log.d(TAG, "Joined buddy group: $joinCode")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Join group failed: ${e.message}")
            false
        }
    }

    fun loadGroup(groupId: String) {
        firestore.collection(COLLECTION_GROUPS).document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                try {
                    val members = (snapshot.get("members") as? List<*>)?.mapNotNull { m ->
                        val map = m as? Map<*, *> ?: return@mapNotNull null
                        BuddyMember(
                            deviceId = map["deviceId"] as? String ?: "",
                            displayName = map["displayName"] as? String ?: "Unknown",
                            fcmToken = map["fcmToken"] as? String ?: "",
                            phoneNumber = map["phoneNumber"] as? String,
                            isCurrentDevice = map["deviceId"] == deviceId,
                        )
                    } ?: emptyList()

                    _myGroup.value = BuddyGroup(
                        id = snapshot.id,
                        name = snapshot.getString("name") ?: "My Group",
                        members = members,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Group parse error: ${e.message}")
                }
            }
    }

    fun leaveGroup() {
        val group = _myGroup.value ?: return
        scope.launch {
            try {
                val docRef = firestore.collection(COLLECTION_GROUPS).document(group.id)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val members = (snapshot.get("members") as? List<*>)?.filter { m ->
                        (m as? Map<*, *>)?.get("deviceId") != deviceId
                    } ?: emptyList<Any>()
                    if (members.isEmpty()) {
                        transaction.delete(docRef)
                    } else {
                        transaction.update(docRef, "members", members)
                    }
                }.await()
                _myGroup.value = null
                Log.d(TAG, "Left buddy group: ${group.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Leave group failed: ${e.message}")
            }
        }
    }

    fun alertGroupMembers(
        lat: Double,
        lon: Double,
        victimName: String = "A group member",
    ) {
        val group = _myGroup.value ?: return
        val otherMembers = group.members.filter { !it.isCurrentDevice }
        if (otherMembers.isEmpty()) return
        Log.d(TAG, "Alerting ${otherMembers.size} buddy group members")
        scope.launch {
            otherMembers.forEach { member ->
                if (member.fcmToken.isNotBlank()) {
                    sendFcmAlert(
                        fcmToken = member.fcmToken,
                        title = "🆘 EMERGENCY ALERT",
                        body = "$victimName may be in danger. OmniMesh auto-SOS triggered.",
                        lat = lat,
                        lon = lon,
                    )
                }
            }
        }
    }

    fun updateLocationForGroup(lat: Double, lon: Double) {
        val group = _myGroup.value ?: return
        firestore.collection(COLLECTION_GROUPS)
            .document(group.id)
            .collection("locations")
            .document(deviceId)
            .set(
                mapOf(
                    "lat" to lat,
                    "lon" to lon,
                    "updatedAt" to System.currentTimeMillis(),
                    "deviceId" to deviceId,
                )
            )
    }

    private fun sendFcmAlert(
        fcmToken: String,
        title: String,
        body: String,
        lat: Double,
        lon: Double,
    ) {
        firestore.collection("pending_fcm_alerts").add(
            mapOf(
                "token" to fcmToken,
                "title" to title,
                "body" to body,
                "lat" to lat,
                "lon" to lon,
                "createdAt" to System.currentTimeMillis(),
            )
        ).addOnFailureListener { e ->
            Log.w(TAG, "FCM alert queuing failed: ${e.message}")
        }
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
