package omnimesh.command1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private data class CommandNode(var x: Float, var y: Float, val type: NodeType)

private enum class NodeType { HEALTHY, CRITICAL, RELAY }

private class MovingPacket(
    var fromX: Float,
    var fromY: Float,
    var toX: Float,
    var toY: Float,
    val type: NodeType,
    var durationMs: Long,
    var elapsedMs: Long = 0L,
)

private data class CollisionRipple(
    val x: Float,
    val y: Float,
    var elapsedMs: Long = 0L,
)

private enum class MeshTooltipVariant { CRITICAL, RELAY, HEALTHY }

private data class MeshTooltip(
    val variant: MeshTooltipVariant,
    val title: String,
    val lines: List<String>,
    val anchorPx: Offset,
)

private class MeshHitSync {
    private val nodeHits = mutableListOf<Triple<Float, Float, NodeType>>()
    private val packetHits = mutableListOf<Pair<Offset, NodeType>>()

    fun sync(nodes: List<CommandNode>, packets: List<MovingPacket>, driftX: Float, driftY: Float) {
        nodeHits.clear()
        nodes.forEach { n ->
            nodeHits.add(Triple(n.x + driftX, n.y + driftY, n.type))
        }
        packetHits.clear()
        packets.forEach { pkt ->
            val t = (pkt.elapsedMs.toFloat() / pkt.durationMs.toFloat()).coerceIn(0f, 1f)
            val px = pkt.fromX + (pkt.toX - pkt.fromX) * t + driftX
            val py = pkt.fromY + (pkt.toY - pkt.fromY) * t + driftY
            packetHits.add(Offset(px, py) to pkt.type)
        }
    }

    fun hit(x: Float, y: Float, activePeers: Int, criticalCount: Int): MeshTooltip? {
        val packetRadius = 14f
        val nodeRadius = mapOf(
            NodeType.CRITICAL to 18f,
            NodeType.RELAY to 12f,
            NodeType.HEALTHY to 10f,
        )
        for ((pos, type) in packetHits) {
            val d = sqrt((x - pos.x).pow(2) + (y - pos.y).pow(2))
            if (d <= packetRadius) return buildPacketTooltip(type, pos, activePeers)
        }
        for ((nx, ny, type) in nodeHits) {
            val r = nodeRadius[type] ?: 12f
            val d = sqrt((x - nx).pow(2) + (y - ny).pow(2))
            if (d <= r) return buildNodeTooltip(type, Offset(nx, ny), activePeers, criticalCount)
        }
        return null
    }

    private fun buildPacketTooltip(type: NodeType, anchor: Offset, activePeers: Int): MeshTooltip {
        val pktId = kotlin.math.abs((anchor.x * 11f + anchor.y * 19f).toInt()) % 99999
        return if (type == NodeType.CRITICAL) {
            MeshTooltip(
                variant = MeshTooltipVariant.CRITICAL,
                title = "PRIORITY PACKET",
                lines = listOf(
                    "Frame $pktId · critical payload",
                    "Vitals / SOS metadata (demo)",
                    "Low-latency path enforced",
                ),
                anchorPx = anchor,
            )
        } else {
            MeshTooltip(
                variant = MeshTooltipVariant.RELAY,
                title = "RELAY PACKET",
                lines = listOf(
                    "Frame $pktId · standard mesh frame",
                    "Multi-hop relay · dedup at hops",
                    "Peers visible: $activePeers",
                ),
                anchorPx = anchor,
            )
        }
    }

    private fun buildNodeTooltip(
        type: NodeType,
        anchor: Offset,
        activePeers: Int,
        criticalCount: Int,
    ): MeshTooltip {
        val peerId = kotlin.math.abs((anchor.x * 31f + anchor.y * 17f).toInt()) % 90000 + 10000
        return when (type) {
            NodeType.CRITICAL -> MeshTooltip(
                variant = MeshTooltipVariant.CRITICAL,
                title = "CRITICAL NODE",
                lines = listOf(
                    "Peer $peerId · priority lane",
                    "Mesh peers: $activePeers · Critical beacons: $criticalCount",
                    "Hold ground entry until clearance (demo)",
                ),
                anchorPx = anchor,
            )
            NodeType.HEALTHY -> MeshTooltip(
                variant = MeshTooltipVariant.RELAY,
                title = "STANDARD RELAY",
                lines = listOf(
                    "Peer $peerId · packet forwarding",
                    "Hop stress normal · redundancy OK",
                ),
                anchorPx = anchor,
            )
            NodeType.RELAY -> MeshTooltip(
                variant = MeshTooltipVariant.HEALTHY,
                title = "HEALTHY NODE",
                lines = listOf(
                    "Peer $peerId · stable uplink",
                    "Participates in failover paths",
                ),
                anchorPx = anchor,
            )
        }
    }
}

@Composable
fun CommandMeshVisualization(
    activePeers: Int,
    criticalCount: Int,
    modifier: Modifier = Modifier,
) {
    val frameMs = 33L
    val nodeCount = 136
    val connectionDistance = 160f
    val sessionStart = remember { System.currentTimeMillis() }

    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    var frameCount by remember { mutableStateOf(0) }
    var timeSeconds by remember { mutableStateOf(0f) }
    var packetsPerSec by remember { mutableStateOf(124) }
    var latencyMs by remember { mutableStateOf(46) }
    var uptime by remember { mutableStateOf("00:00:00") }
    var stressBoost by remember { mutableStateOf(0f) }
    var lastCriticalCount by remember { mutableStateOf(criticalCount) }

    val nodes = remember { mutableListOf<CommandNode>() }
    val packets = remember { mutableListOf<MovingPacket>() }
    val ripples = remember { mutableStateListOf<CollisionRipple>() }
    val hitSync = remember { MeshHitSync() }
    var meshTooltip by remember { mutableStateOf<MeshTooltip?>(null) }
    val peersUpdated = rememberUpdatedState(activePeers)
    val critUpdated = rememberUpdatedState(criticalCount)

    LaunchedEffect(w, h) {
        if (w == 0f || h == 0f) return@LaunchedEffect
        nodes.clear()
        repeat((nodeCount * 0.80f).toInt()) {
            nodes.add(
                CommandNode(
                    x = (Math.random() * w).toFloat(),
                    y = (Math.random() * h).toFloat(),
                    type = NodeType.HEALTHY
                )
            )
        }
        repeat((nodeCount * 0.05f).toInt()) {
            nodes.add(
                CommandNode(
                    x = (Math.random() * w).toFloat(),
                    y = (Math.random() * h).toFloat(),
                    type = NodeType.CRITICAL
                )
            )
        }
        while (nodes.size < nodeCount) {
            nodes.add(
                CommandNode(
                    x = (Math.random() * w).toFloat(),
                    y = (Math.random() * h).toFloat(),
                    type = NodeType.RELAY
                )
            )
        }
        packets.clear()
        ripples.clear()
        repeat(8) {
            val from = nodes.random()
            val to = nodes.filter { it != from }.randomOrNull() ?: nodes.first()
            packets.add(
                MovingPacket(
                    fromX = from.x,
                    fromY = from.y,
                    toX = to.x,
                    toY = to.y,
                    type = NodeType.RELAY,
                    durationMs = (2000L..4000L).random().toLong(),
                )
            )
        }
        repeat(4) {
            val from = nodes.random()
            val to = nodes.filter { it != from }.randomOrNull() ?: nodes.first()
            packets.add(
                MovingPacket(
                    fromX = from.x,
                    fromY = from.y,
                    toX = to.x,
                    toY = to.y,
                    type = NodeType.CRITICAL,
                    durationMs = (2000L..4000L).random().toLong(),
                )
            )
        }
    }

    LaunchedEffect(w, h) {
        while (true) {
            delay(frameMs)
            if (packets.isEmpty() || nodes.isEmpty()) continue
            timeSeconds += frameMs / 1000f
            packets.forEach { pkt ->
                pkt.elapsedMs += frameMs
                if (pkt.elapsedMs >= pkt.durationMs) {
                    ripples.add(CollisionRipple(x = pkt.toX, y = pkt.toY))
                    pkt.elapsedMs = 0L
                    val newFrom = nodes.random()
                    val newTo = nodes.filter { it != newFrom }.randomOrNull() ?: nodes.first()
                    pkt.fromX = newFrom.x
                    pkt.fromY = newFrom.y
                    pkt.toX = newTo.x
                    pkt.toY = newTo.y
                    pkt.durationMs = (2000L..4000L).random().toLong()
                }
            }
            for (i in ripples.indices.reversed()) {
                ripples[i].elapsedMs += frameMs
                if (ripples[i].elapsedMs >= 300L) ripples.removeAt(i)
            }

            val elapsedSec = ((System.currentTimeMillis() - sessionStart) / 1000L).coerceAtLeast(0L)
            val hours = elapsedSec / 3600
            val minutes = (elapsedSec % 3600) / 60
            val seconds = elapsedSec % 60
            uptime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

            if (frameCount % 30 == 0) {
                val trafficBase = 90 + activePeers * 3 + criticalCount * 7
                packetsPerSec = (trafficBase + (-9..12).random()).coerceIn(45, 420)
                latencyMs = (42 + criticalCount * 3 + (-6..8).random()).coerceIn(28, 160)
            }

            stressBoost = (stressBoost * 0.95f).coerceAtLeast(0f)
            val driftSyncX = 30f * sin((2f * Math.PI.toFloat() / 20f) * timeSeconds)
            val driftSyncY =
                30f * sin((4f * Math.PI.toFloat() / 20f) * timeSeconds + (Math.PI.toFloat() / 2f))
            hitSync.sync(nodes, packets, driftSyncX, driftSyncY)
            frameCount++
        }
    }

    LaunchedEffect(criticalCount) {
        if (criticalCount > lastCriticalCount) {
            stressBoost = (stressBoost + 0.55f + (criticalCount - lastCriticalCount) * 0.08f).coerceAtMost(1.4f)
        }
        lastCriticalCount = criticalCount
    }

    val frameSnapshot = frameCount

    Box(
        modifier = modifier
            .background(Color(0xFF05080E), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF4285F4).copy(alpha = 0.28f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "ZONE 4 — MESH OVERVIEW",
                color = Color(0xFFE8EAED),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                "Live packet flow · self-healing routing",
                color = Color(0xFF9AA0A6),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color(0xFF0D1117).copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                "PACKETS/SEC $packetsPerSec",
                color = Color(0xFF8AB4F8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "LATENCY ${latencyMs}ms",
                color = Color(0xFFFBBC04),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "UPTIME $uptime",
                color = Color(0xFF34A853),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp)
                .onSizeChanged { size ->
                    w = size.width.toFloat()
                    h = size.height.toFloat()
                }
        ) {
            val driftX = 30f * sin((2f * Math.PI.toFloat() / 20f) * timeSeconds)
            val driftY = 30f * sin((4f * Math.PI.toFloat() / 20f) * timeSeconds + (Math.PI.toFloat() / 2f))
            val scanProgress = (timeSeconds % 6f) / 6f
            val scanX = scanProgress * size.width
            val stressRadius = 110f + criticalCount * 6f
            val critPulse = (sin(timeSeconds.toDouble() * 5.2).toFloat() * 0.5f + 0.5f)

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF172334),
                        Color(0xFF0C121C),
                        Color(0xFF040609),
                    ),
                    center = Offset(size.width * 0.48f, size.height * 0.42f),
                    radius = max(size.width, size.height) * 0.92f,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4285F4).copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.55f),
                    radius = max(size.width, size.height) * 0.65f,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
            )

            val criticalNodes = nodes.filter { it.type == NodeType.CRITICAL }

            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    val a = nodes[i]
                    val b = nodes[j]
                    val dist = sqrt((b.x - a.x).pow(2) + (b.y - a.y).pow(2))
                    if (dist < connectionDistance) {
                        val fade = (1f - dist / connectionDistance) * 0.16f
                        val aToCritical = criticalNodes.minOfOrNull { c -> sqrt((a.x - c.x).pow(2) + (a.y - c.y).pow(2)) } ?: 9999f
                        val bToCritical = criticalNodes.minOfOrNull { c -> sqrt((b.x - c.x).pow(2) + (b.y - c.y).pow(2)) } ?: 9999f
                        val nearCritical = 1f - ((minOf(aToCritical, bToCritical) / stressRadius).coerceIn(0f, 1f))
                        val stress = (nearCritical * (0.35f + stressBoost)).coerceIn(0f, 1f)
                        val edgeColor = if (stress > 0.05f) {
                            Color(
                                red = 0.26f + 0.65f * stress,
                                green = 0.52f - 0.35f * stress,
                                blue = 0.96f - 0.70f * stress,
                                alpha = fade + (0.16f * stress)
                            )
                        } else {
                            Color(0xFF4285F4).copy(alpha = fade)
                        }
                        drawLine(
                            color = edgeColor,
                            start = Offset(a.x + driftX, a.y + driftY),
                            end = Offset(b.x + driftX, b.y + driftY),
                            strokeWidth = 0.75f + 1.35f * stress
                        )
                    }
                }
            }

            nodes.forEach { node ->
                val nodeOffset = Offset(node.x + driftX, node.y + driftY)
                val scanDx = kotlin.math.abs(nodeOffset.x - scanX)
                val scanGlow = (1f - (scanDx / 52f)).coerceIn(0f, 1f)
                when (node.type) {
                    NodeType.HEALTHY -> {
                        drawCircle(Color(0xFF4285F4).copy(alpha = 0.12f + 0.1f * scanGlow), 7f, nodeOffset)
                        drawCircle(Color(0xFF8AB4F8).copy(alpha = 0.35f + 0.2f * scanGlow), 3f, nodeOffset)
                        drawCircle(Color(0xFF4285F4).copy(alpha = 0.72f), 2.2f, nodeOffset)
                    }
                    NodeType.RELAY -> {
                        drawCircle(Color(0xFF34A853).copy(alpha = 0.14f + 0.12f * scanGlow), 8f, nodeOffset)
                        drawCircle(Color(0xFF4285F4).copy(alpha = 0.18f), 4.5f, nodeOffset)
                        drawCircle(Color(0xFFE8F0FE).copy(alpha = 0.55f + 0.2f * scanGlow), 2.6f, nodeOffset)
                    }
                    NodeType.CRITICAL -> {
                        val halo = 11f + critPulse * 4f + 2.5f * scanGlow
                        drawCircle(Color(0xFFEA4335).copy(alpha = 0.14f + 0.12f * critPulse), halo, nodeOffset)
                        drawCircle(Color(0xFFFF5252).copy(alpha = 0.28f + 0.15f * scanGlow), 6f, nodeOffset)
                        drawCircle(Color(0xFFFFEBEE).copy(alpha = 0.9f), 3.6f + scanGlow * 1.2f, nodeOffset)
                    }
                }
            }

            packets.forEach { pkt ->
                val t = (pkt.elapsedMs.toFloat() / pkt.durationMs.toFloat()).coerceIn(0f, 1f)
                val px = pkt.fromX + (pkt.toX - pkt.fromX) * t + driftX
                val py = pkt.fromY + (pkt.toY - pkt.fromY) * t + driftY
                if (pkt.type == NodeType.CRITICAL) {
                    drawCircle(Color(0xFFEA4335).copy(alpha = 0.35f), 9f, Offset(px, py))
                    drawCircle(Color(0xFFEA4335), 5f, Offset(px, py))
                } else {
                    drawCircle(Color(0xFF4285F4).copy(alpha = 0.25f), 7f, Offset(px, py))
                    drawCircle(Color(0xFF4285F4), 4f, Offset(px, py))
                }
            }

            ripples.forEach { ripple ->
                val t = (ripple.elapsedMs / 300f).coerceIn(0f, 1f)
                val alpha = (0.60f * (1f - t)).coerceAtLeast(0f)
                val baseRadius = 20f * t
                repeat(3) { idx ->
                    drawCircle(
                        color = Color(0xFF8AB4F8).copy(alpha = alpha * (1f - idx * 0.15f)),
                        radius = (baseRadius + idx * 5f).coerceAtMost(20f),
                        center = Offset(ripple.x + driftX, ripple.y + driftY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (1.6f - idx * 0.3f).coerceAtLeast(0.6f))
                    )
                }
            }

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF8AB4F8).copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    startX = scanX - 12f,
                    endX = scanX + 12f
                ),
                topLeft = Offset(0f, 0f),
                size = size
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp)
                .pointerInput(w, h) {
                    detectTapGestures { offset ->
                        meshTooltip = hitSync.hit(
                            offset.x,
                            offset.y,
                            peersUpdated.value,
                            critUpdated.value,
                        )
                    }
                }
        )

        meshTooltip?.let { tip ->
            val (bg, borderC) = when (tip.variant) {
                MeshTooltipVariant.CRITICAL ->
                    Color(0xFFB31412) to Color(0xFFFF8A80)
                MeshTooltipVariant.RELAY ->
                    Color(0xFF1A237E).copy(alpha = 0.94f) to Color(0xFF8AB4F8)
                MeshTooltipVariant.HEALTHY ->
                    Color(0xFF1B5E20).copy(alpha = 0.94f) to Color(0xFF81C995)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 44.dp)
                    .offset {
                        IntOffset(
                            (tip.anchorPx.x + 12f).roundToInt().coerceAtLeast(0),
                            (tip.anchorPx.y - 108f).roundToInt().coerceAtLeast(0),
                        )
                    }
            ) {
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, borderC.copy(alpha = 0.88f)),
                    modifier = Modifier.widthIn(max = 260.dp),
                    shadowElevation = 4.dp,
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tip.title,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "✕",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { meshTooltip = null }
                                    .padding(4.dp),
                            )
                        }
                        tip.lines.forEach { line ->
                            Text(
                                line,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color(0xFF0D1117).copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "DATA TRAFFIC",
                color = Color(0xFF9AA0A6),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            LegendRow(Color(0xFFEA4335), "Critical priority")
            LegendRow(Color(0xFF4285F4), "Standard relay")
            LegendRow(Color(0xFF34A853), "Healthy node")
            Spacer(Modifier.height(4.dp))
            Text(
                "Active peers: $activePeers · Critical: $criticalCount",
                color = Color(0xFFE8EAED),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = Color(0xFFE8EAED), fontSize = 11.sp)
    }
}
