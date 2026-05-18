package omnimesh.command1.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import omnimesh.command1.command.TimelineEventType
import omnimesh.command1.OmniMeshApp
import omnimesh.command1.data.PacketRepository
import omnimesh.command1.data.TriagePacket
import omnimesh.command1.location.PeerRssiObservation
import omnimesh.command1.location.RssiTriangulator
import omnimesh.command1.responder.AudioChunk
import omnimesh.command1.utils.DeviceUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 💡 SERVICE_ID is like a radio frequency — only OmniMesh apps
// talk to each other. Other Nearby apps on the same phone are ignored.
private const val SERVICE_ID = "com.omnimesh.mesh.v1"
private const val TAG = "OmniMesh_Mesh"

// How many times we retry a failed packet send before giving up
private const val MAX_SEND_RETRIES = 3

class NearbyMeshManager(
    private val context: Context,
    private val repository: PacketRepository,
    private val onPacketReceived: (TriagePacket) -> Unit
) {
    var walkieTalkieManager: WalkieTalkieManager? = null

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val deviceId = DeviceUtils.getDeviceId(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 💡 StateFlow is like a live variable — anyone observing it
    // gets notified instantly when it changes. We use this to
    // show "3 devices connected" in the UI without polling.
    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints

    private val _meshStatus = MutableStateFlow(MeshStatus.IDLE)
    val meshStatus: StateFlow<MeshStatus> = _meshStatus

    // Track send retries per packet
    private val retryCount = mutableMapOf<String, Int>()

    // ─────────────────────────────────────────────
    // ADVERTISING — "I am here, connect to me"
    // ─────────────────────────────────────────────

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            // 💡 P2P_CLUSTER = all devices are equal peers (no hub/spoke).
            // Every phone can talk to every other phone directly.
            // This is what makes it a true mesh, not a star topology.
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            DeviceUtils.getEndpointName(context),
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            _meshStatus.value = MeshStatus.ADVERTISING
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener { e ->
            _meshStatus.value = MeshStatus.ERROR
            Log.e(TAG, "Advertising failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────
    // DISCOVERY — "Looking for other OmniMesh nodes"
    // ─────────────────────────────────────────────

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────
    // CALLBACKS — what happens when devices are found
    // ─────────────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // 💡 Only connect to other OmniMesh nodes, not random Nearby apps
            if (info.serviceId == SERVICE_ID) {
                Log.d(TAG, "Found endpoint: $endpointId — requesting connection")
                connectionsClient.requestConnection(
                    DeviceUtils.getEndpointName(context),
                    endpointId,
                    connectionLifecycleCallback
                )
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // 💡 In a disaster, there's no time for manual "Accept connection?" dialogs.
            // We auto-accept all OmniMesh connections. This is a deliberate design choice.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            Log.d(TAG, "Connection initiated with $endpointId — auto-accepting")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                _connectedEndpoints.value = _connectedEndpoints.value + endpointId
                _meshStatus.value = MeshStatus.CONNECTED
                Log.d(TAG, "Connected to $endpointId. Total peers: ${_connectedEndpoints.value.size}")

                // 💡 As soon as a new peer connects, immediately send them
                // any RED packets we're holding. Don't wait for a scheduled relay.
                scope.launch { flushRedPacketsTo(endpointId) }
            } else {
                Log.w(TAG, "Connection to $endpointId failed: ${result.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
            if (_connectedEndpoints.value.isEmpty()) {
                _meshStatus.value = MeshStatus.ADVERTISING
            }
            Log.d(TAG, "Disconnected from $endpointId")
        }
    }

    // ─────────────────────────────────────────────
    // RECEIVING — incoming packets from other nodes
    // ─────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                if (bytes.size > WalkieTalkieManager.AUDIO_PAYLOAD_PREFIX.length) {
                    val prefix = String(
                        bytes.slice(0 until WalkieTalkieManager.AUDIO_PAYLOAD_PREFIX.length).toByteArray()
                    )
                    if (prefix == WalkieTalkieManager.AUDIO_PAYLOAD_PREFIX) {
                        val audioBytes =
                            bytes.slice(
                                WalkieTalkieManager.AUDIO_PAYLOAD_PREFIX.length until bytes.size
                            ).toByteArray()
                        walkieTalkieManager?.onAudioPayloadReceived(audioBytes)
                        return
                    }
                }
                scope.launch {
                    try {
                        val packet = TriagePacket.fromBytes(bytes)

                        // 💡 Deduplication — if we already have this packet ID,
                        // ignore it. This stops infinite mesh loops where
                        // Phone A → B → C → A → B → C forever.
                        if (repository.isAlreadyReceived(packet.id)) {
                            Log.d(TAG, "Duplicate packet ${packet.id} — ignoring")
                            return@launch
                        }

                        // Save to local DB
                        repository.save(packet)
                        if (packet.urgency == "RED") {
                            (context as? OmniMeshApp)?.timelineManager?.record(
                                type = TimelineEventType.RED_PACKET_DETECTED,
                                title = "RED Packet via Mesh",
                                detail = "Received from ${packet.originDeviceId.take(6)} · hop ${packet.hopCount}",
                                urgency = "RED",
                                lat = packet.lat,
                                lon = packet.lon,
                                packetId = packet.id,
                            )
                        }

                        // ADD: After deserializing a TriagePacket, extract the sender's GPS from the
                        // packet and record an RSSI observation with measured (or approximated) RSSI.
                        recordPeerRssiObservation(endpointId, packet)

                        // Notify UI (e.g. update map pin)
                        withContext(Dispatchers.Main) { onPacketReceived(packet) }

                        // 💡 Relay to all OTHER connected endpoints — not back
                        // to whoever sent it to us. This is the "mesh" behavior.
                        relayToOthers(packet, excludeEndpoint = endpointId)

                        Log.d(TAG, "Received + relayed packet ${packet.id} (${packet.urgency})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse incoming packet: ${e.message}")
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // For small JSON packets this completes instantly — no progress tracking needed
        }
    }

    // ─────────────────────────────────────────────
    // SENDING — push packets to peers
    // ─────────────────────────────────────────────

    // 💡 This is where the priority queue matters.
    // We pull from the DB in RED→YELLOW→GREEN→BLACK order
    // and send them all to every connected peer.
    suspend fun broadcastPriorityQueue() {
        val queue = repository.getTransmitQueue()
        if (queue.isEmpty() || _connectedEndpoints.value.isEmpty()) return

        Log.d(TAG, "Broadcasting ${queue.size} packets to ${_connectedEndpoints.value.size} peers")

        for (packet in queue) {
            sendToAll(packet)
        }
    }

    fun broadcastAudioChunk(chunk: AudioChunk) {
        val bytes = WalkieTalkieManager.AUDIO_PAYLOAD_PREFIX.toByteArray() + chunk.toBytes()
        val payload = Payload.fromBytes(bytes)
        _connectedEndpoints.value.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, payload)
                .addOnFailureListener { /* audio loss is acceptable */ }
        }
    }

    private fun sendToAll(packet: TriagePacket) {
        val payload = Payload.fromBytes(packet.toBytes())
        _connectedEndpoints.value.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, payload)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Send to $endpointId failed: ${e.message}")
                    handleSendFailure(packet, endpointId)
                }
        }
    }

    private fun relayToOthers(packet: TriagePacket, excludeEndpoint: String) {
        val targets = _connectedEndpoints.value - excludeEndpoint
        if (targets.isEmpty()) return

        val payload = Payload.fromBytes(packet.toBytes())
        targets.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, payload)
        }
    }

    // Immediately flush RED packets to a newly connected peer
    private suspend fun flushRedPacketsTo(endpointId: String) {
        val redPackets = repository.getTransmitQueue()
            .filter { it.urgency == "RED" }

        redPackets.forEach { packet ->
            val payload = Payload.fromBytes(packet.toBytes())
            connectionsClient.sendPayload(endpointId, payload)
        }
        Log.d(TAG, "Flushed ${redPackets.size} RED packets to $endpointId")
    }

    private fun handleSendFailure(packet: TriagePacket, endpointId: String) {
        val key = "${packet.id}_$endpointId"
        val attempts = retryCount.getOrDefault(key, 0) + 1
        retryCount[key] = attempts

        if (attempts < MAX_SEND_RETRIES) {
            // 💡 Exponential backoff — wait longer each retry.
            // 1st retry: 2s, 2nd: 4s, 3rd: 8s. Avoids hammering a struggling connection.
            val delayMs = (Math.pow(2.0, attempts.toDouble()) * 1000).toLong()
            scope.launch {
                delay(delayMs)
                val payload = Payload.fromBytes(packet.toBytes())
                connectionsClient.sendPayload(endpointId, payload)
            }
        } else {
            Log.w(TAG, "Gave up on packet ${packet.id} to $endpointId after $MAX_SEND_RETRIES retries")
            retryCount.remove(key)
        }
    }

    // ─────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────

    fun start() {
        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedEndpoints.value = emptySet()
        _meshStatus.value = MeshStatus.IDLE
        scope.cancel()
        Log.d(TAG, "Mesh stopped")
    }

    /**
     * Records a peer RSSI observation for indoor triangulation.
     *
     * TODO: Production — run a parallel BLE scan for real RSSI. Nearby Connections does not
     * expose raw RSSI in the public API; we approximate using a deterministic demo RSSI spread.
     * Optionally incorporate payload round-trip timing as a quality hint once measured.
     */
    private fun recordPeerRssiObservation(endpointId: String, packet: TriagePacket) {
        // Demo: deterministic RSSI in [-40,-75] by endpoint; replace with BLE scan RSSI in production.
        val rssiDbm = -40 - (kotlin.math.abs(endpointId.hashCode()) % 36)
        val distanceEstimate =
            RssiTriangulator.rssiToDistanceMeters(rssiDbm, isCollapseScenario = true)
        val observation = PeerRssiObservation(
            peerId = endpointId,
            peerLat = packet.lat,
            peerLon = packet.lon,
            peerFloorEstimate = null,
            rssi = rssiDbm,
            estimatedDistanceMeters = distanceEstimate,
        )
        val app = context.applicationContext as? OmniMeshApp
        app?.indoorLocationManager?.onPeerRssiObservation(observation)
    }
}

// 💡 Sealed class = a fixed set of possible states.
// The UI observes this and shows "Searching...", "Connected (3 peers)", etc.
enum class MeshStatus {
    IDLE,           // Not started
    ADVERTISING,    // Running, looking for peers
    CONNECTED,      // At least one peer connected
    ERROR           // Something went wrong
}
