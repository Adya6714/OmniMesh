package omnimesh.command1.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import omnimesh.command1.data.TriagePacket
import omnimesh.command1.responder.SectorStatus
import omnimesh.command1.ui.components.AcousticVisualizer
import omnimesh.command1.ui.components.MeshBackground
import omnimesh.command1.ui.components.OmniMeshTopBar
import omnimesh.command1.ui.theme.GoogleSans
import omnimesh.command1.ui.theme.OmniMeshColors
import omnimesh.command1.ui.theme.urgencyColor
import kotlin.math.sin

/*
 * Maps SDK for Android must be enabled in Google Cloud Console → APIs &
 * Services → Library separately from the Maps JavaScript API, or the map
 * may show a developer / authorization error even with a valid API key.
 */

// Dark map style — the "Night Mode" preset Google ships with the Maps SDK
// docs. Keeps responders visually anchored in low-light operational
// environments and matches the tactical Command theme.
private const val DARK_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#1d2c4d"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8ec3b9"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1a3646"}]},
  {"featureType":"administrative.country","elementType":"geometry.stroke","stylers":[{"color":"#4b6878"}]},
  {"featureType":"administrative.land_parcel","elementType":"labels.text.fill","stylers":[{"color":"#64779e"}]},
  {"featureType":"administrative.province","elementType":"geometry.stroke","stylers":[{"color":"#4b6878"}]},
  {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#334e87"}]},
  {"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"#023e58"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#283d6a"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#6f9ba5"}]},
  {"featureType":"poi","elementType":"labels.text.stroke","stylers":[{"color":"#1d2c4d"}]},
  {"featureType":"poi.park","elementType":"geometry.fill","stylers":[{"color":"#023e58"}]},
  {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#3C7680"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#304a7d"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#98a5be"}]},
  {"featureType":"road","elementType":"labels.text.stroke","stylers":[{"color":"#1d2c4d"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#2c6675"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#255763"}]},
  {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#b0d5ce"}]},
  {"featureType":"road.highway","elementType":"labels.text.stroke","stylers":[{"color":"#023e58"}]},
  {"featureType":"transit","elementType":"labels.text.fill","stylers":[{"color":"#98a5be"}]},
  {"featureType":"transit","elementType":"labels.text.stroke","stylers":[{"color":"#1d2c4d"}]},
  {"featureType":"transit.line","elementType":"geometry.fill","stylers":[{"color":"#283d6a"}]},
  {"featureType":"transit.station","elementType":"geometry","stylers":[{"color":"#3a4762"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1626"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4e6d70"}]}
]
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponderScreen(
    packets: List<TriagePacket>,
    viewModel: OmniMeshViewModel,
    demoMode: Boolean,
    onToggleDemoMode: () -> Unit
) {
    val sectorLabel = remember { "G-4" }
    var isInField by remember { mutableStateOf(false) }
    var showClaimDialog by remember { mutableStateOf(false) }
    var claimLabel by remember { mutableStateOf("") }
    val waypointList by viewModel.breadcrumbWaypoints.collectAsState(initial = emptyList())
    val claims by viewModel.sectorClaims.collectAsState(initial = emptyList())
    val zones by viewModel.activeDangerZones.collectAsState(initial = emptyList())
    val isTransmitting by viewModel.isWalkieTalkieTransmitting.collectAsState()
    val isReceiving by viewModel.isWalkieTalkieReceiving.collectAsState()
    val activeSpeaker by viewModel.activeSpeaker.collectAsState()
    val pttPulse = rememberInfiniteTransition(label = "ptt")
    val pttScale by pttPulse.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pttScale"
    )

    // Sort RED → YELLOW → GREEN; auto-generated bubbles up by adding a
    // confidence-driven secondary sort.
    val sorted = remember(packets) {
        packets.sortedWith(
            compareBy<TriagePacket> { it.urgencyPriority() }
                .thenByDescending { it.confidence }
        )
    }
    val redCount = sorted.count { it.urgency == "RED" }
    val autoCount = sorted.count { it.isAutoGenerated }
    val peerCount by viewModel.peerCount.collectAsState()
    val responderId = remember { "RSP-07" }

    var selected by remember { mutableStateOf<TriagePacket?>(null) }
    var showQueueSection by remember { mutableStateOf(true) }
    var showMapSection by remember { mutableStateOf(true) }
    var showAnalysisSection by remember { mutableStateOf(true) }
    var heartbeatSpikeAmp by remember { mutableStateOf(0.22f) }
    var heartbeatEventKey by remember { mutableIntStateOf(0) }
    var knownPacketIds by remember { mutableStateOf(setOf<String>()) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            sorted.firstOrNull()?.let { LatLng(it.lat, it.lon) } ?: LatLng(0.0, 0.0),
            15f
        )
    }
    var animatedWaypointCount by remember { mutableIntStateOf(0) }
    var hasCenteredOnPackets by remember { mutableStateOf(false) }

    LaunchedEffect(sorted.firstOrNull()?.id) {
        val first = sorted.firstOrNull()
        if (first != null && !hasCenteredOnPackets) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(first.lat, first.lon), 16f)
            )
            hasCenteredOnPackets = true
        }
    }

    LaunchedEffect(sorted.map { it.id }) {
        val currentIds = sorted.map { it.id }.toSet()
        if (knownPacketIds.isNotEmpty()) {
            val newPackets = sorted.filter { it.id !in knownPacketIds }
            if (newPackets.isNotEmpty()) {
                heartbeatSpikeAmp = when {
                    newPackets.any { it.urgency == "RED" } -> 1.0f
                    newPackets.any { it.urgency == "YELLOW" } -> 0.65f
                    else -> 0.35f
                }
                heartbeatEventKey += 1
            }
        }
        knownPacketIds = currentIds
    }

    LaunchedEffect(waypointList.size) {
        if (waypointList.isEmpty()) {
            animatedWaypointCount = 0
            return@LaunchedEffect
        }
        while (animatedWaypointCount < waypointList.size) {
            animatedWaypointCount += 1
            delay(220)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MeshBackground(
            modifier = Modifier.fillMaxSize(),
            nodeCount = 24,
            nodeColor = Color(0xFF4285F4),
            edgeColor = Color(0xFF34A853),
            nodeAlpha = 0.10f,
            edgeAlpha = 0.04f,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniMeshColors.DarkBackground.copy(alpha = 0.88f))
        ) {
        OmniMeshTopBar(
            screenName = "RESPONDER",
            peerCount = peerCount,
        )
        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(
            visible = isInField,
            enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(animationSpec = tween(280)),
            exit = fadeOut(animationSpec = tween(220))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F6C3D))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "FIELD ACTIVE · $responderId",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (redCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFA50E0E))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "⚡ $redCount CRITICAL ON WIRE — PRIORITY ROUTING ACTIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple(redCount, "RED", Color(0xFFEA4335)),
                Triple(sorted.count { it.urgency == "YELLOW" }, "YEL", Color(0xFFFBBC04)),
                Triple(sorted.count { it.urgency == "GREEN" }, "OK", Color(0xFF34A853)),
            ).forEach { (count, label, color) ->
                Row(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$count", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(label, color = color.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val wide = false
            if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "LIVE TRIAGE QUEUE",
                        color = OmniMeshColors.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        fontFamily = GoogleSans
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(sorted, key = { _, p -> p.id }) { index, packet ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(220, delayMillis = index * 50)) +
                                    slideInHorizontally(animationSpec = tween(260, delayMillis = index * 50)) { it / 3 }
                            ) {
                                TriageQueueCard(packet = packet, onClick = { selected = packet })
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(0.50f)
                        .fillMaxHeight()
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            mapType = MapType.NORMAL,
                            isMyLocationEnabled = false,
                            mapStyleOptions = MapStyleOptions(DARK_MAP_STYLE_JSON.trim())
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            compassEnabled = true,
                            mapToolbarEnabled = false
                        )
                    ) {
                        if (waypointList.size >= 2) {
                            Polyline(
                                points = waypointList.map { LatLng(it.lat, it.lon) },
                                color = Color(0xFF4285F4).copy(alpha = 0.7f),
                                width = 4f,
                                pattern = listOf(Dot(), Gap(8f))
                            )
                        }
                        claims.forEach { claim ->
                            val circleColor = when (claim.status) {
                                SectorStatus.SEARCHING -> Color(0xFF4285F4)
                                SectorStatus.CLEARED -> Color(0xFF34A853)
                                SectorStatus.DANGEROUS -> Color(0xFFEA4335)
                            }
                            Circle(
                                center = LatLng(claim.centerLat, claim.centerLon),
                                radius = claim.radiusMeters.toDouble(),
                                fillColor = circleColor.copy(alpha = 0.15f),
                                strokeColor = circleColor.copy(alpha = 0.6f),
                                strokeWidth = 2f,
                            )
                        }
                        zones.forEach { zone ->
                            Circle(
                                center = LatLng(zone.centerLat, zone.centerLon),
                                radius = zone.radiusMeters.toDouble(),
                                fillColor = Color(0xFFFBBC04).copy(alpha = 0.20f),
                                strokeColor = Color(0xFFFBBC04).copy(alpha = 0.8f),
                                strokeWidth = 3f,
                            )
                        }
                        sorted.forEach { packet ->
                            TriageMarker(packet = packet, onClick = { selected = packet })
                        }
                    }
                    MapOverlayLabel(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .weight(0.15f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = OmniMeshColors.DarkSurface,
                    border = BorderStroke(1.dp, OmniMeshColors.MediumBlue.copy(alpha = 0.28f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ON-DEMAND ANALYSIS", color = OmniMeshColors.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Critical alert", color = OmniMeshColors.MediumRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${redCount} RED + ${autoCount} AUTO cases co-located", color = OmniMeshColors.Grey, fontSize = 12.sp)
                    }
                }
            }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OmniMeshColors.DarkSurface,
                    border = BorderStroke(1.dp, OmniMeshColors.MediumBlue.copy(alpha = 0.25f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showQueueSection = !showQueueSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SECTION 1 · ALL PACKETS", color = OmniMeshColors.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(if (showQueueSection) "▲" else "▼", color = OmniMeshColors.MediumBlue, fontSize = 13.sp)
                        }
                        AnimatedVisibility(showQueueSection) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HeartbeatMonitorLine(
                                    eventKey = heartbeatEventKey,
                                    eventAmplitude = heartbeatSpikeAmp
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(sorted, key = { _, p -> p.id }) { index, packet ->
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(animationSpec = tween(220, delayMillis = index * 50)) +
                                                slideInHorizontally(animationSpec = tween(260, delayMillis = index * 50)) { it / 3 }
                                        ) {
                                            TriageQueueCard(packet = packet, onClick = { selected = packet })
                                        }
                                    }
                                }
                                if (autoCount > 0) {
                                    ProximityAlertsFooter(autoCount = autoCount)
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OmniMeshColors.DarkSurface,
                    border = BorderStroke(1.dp, OmniMeshColors.MediumBlue.copy(alpha = 0.25f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showMapSection = !showMapSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SECTION 2 · MAP", color = OmniMeshColors.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(if (showMapSection) "▲" else "▼", color = OmniMeshColors.MediumBlue, fontSize = 13.sp)
                        }
                        AnimatedVisibility(showMapSection) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    properties = MapProperties(
                                        mapType = MapType.NORMAL,
                                        isMyLocationEnabled = false,
                                        mapStyleOptions = MapStyleOptions(DARK_MAP_STYLE_JSON.trim())
                                    ),
                                    uiSettings = MapUiSettings(
                                        zoomControlsEnabled = false,
                                        compassEnabled = true,
                                        mapToolbarEnabled = false
                                    )
                                ) {
                                    claims.forEach { claim ->
                                        val circleColor = when (claim.status) {
                                            SectorStatus.SEARCHING -> Color(0xFF4285F4)
                                            SectorStatus.CLEARED -> Color(0xFF34A853)
                                            SectorStatus.DANGEROUS -> Color(0xFFEA4335)
                                        }
                                        Circle(
                                            center = LatLng(claim.centerLat, claim.centerLon),
                                            radius = claim.radiusMeters.toDouble(),
                                            fillColor = circleColor.copy(alpha = 0.15f),
                                            strokeColor = circleColor.copy(alpha = 0.6f),
                                            strokeWidth = 2f,
                                        )
                                    }
                                    SonarSweep(center = cameraPositionState.position.target)
                                    zones.forEach { zone ->
                                        Circle(
                                            center = LatLng(zone.centerLat, zone.centerLon),
                                            radius = zone.radiusMeters.toDouble(),
                                            fillColor = Color(0xFFFBBC04).copy(alpha = 0.20f),
                                            strokeColor = Color(0xFFFBBC04).copy(alpha = 0.8f),
                                            strokeWidth = 3f,
                                        )
                                    }
                                    AnimatedWaypointTrail(
                                        waypoints = waypointList.map { LatLng(it.lat, it.lon) },
                                        visibleCount = animatedWaypointCount
                                    )
                                    sorted.forEach { packet ->
                                        TriageMarker(packet = packet, onClick = { selected = packet })
                                    }
                                }
                                MapOverlayLabel(modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OmniMeshColors.DarkSurface,
                    border = BorderStroke(1.dp, OmniMeshColors.MediumBlue.copy(alpha = 0.25f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showAnalysisSection = !showAnalysisSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SECTION 3 · ON-DEVICE ANALYSIS", color = OmniMeshColors.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(if (showAnalysisSection) "▲" else "▼", color = OmniMeshColors.MediumBlue, fontSize = 13.sp)
                        }
                        AnimatedVisibility(showAnalysisSection) {
                            OnDeviceAnalysisStrip(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isInField) Color(0xFF34A853).copy(alpha = 0.2f) else Color(0xFF174EA6).copy(alpha = 0.2f),
                                RoundedCornerShape(6.dp)
                            )
                            .border(1.dp, if (isInField) Color(0xFF34A853) else Color(0xFF4285F4), RoundedCornerShape(6.dp))
                            .clickable {
                                isInField = !isInField
                                if (isInField) viewModel.startFieldTracking() else viewModel.stopFieldTracking()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (isInField) "● IN FIELD" else "ENTER FIELD", color = if (isInField) Color(0xFF34A853) else Color(0xFF4285F4), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    if (isInField) {
                        FloatingActionButton(
                            onClick = { showClaimDialog = true },
                            containerColor = Color(0xFF174EA6),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.AddLocation, contentDescription = "Claim Sector", tint = Color.White)
                        }
                    }
                }

                AnimatedVisibility(visible = isReceiving && activeSpeaker != null) {
                    Text("🔊 $activeSpeaker speaking...", color = Color(0xFF34A853), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .graphicsLayer(scaleX = if (isTransmitting) pttScale else 1f, scaleY = if (isTransmitting) pttScale else 1f)
                        .background(if (isTransmitting) Color(0xFFEA4335) else Color(0xFF1C2025), RoundedCornerShape(8.dp))
                        .border(2.dp, if (isTransmitting) Color(0xFFEA4335) else Color(0xFF4285F4), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    viewModel.startPtt()
                                    tryAwaitRelease()
                                    viewModel.stopPtt()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = if (isTransmitting) Color.White else Color(0xFF4285F4), modifier = Modifier.size(18.dp))
                        Text(
                            if (isTransmitting) "TRANSMITTING — RELEASE TO STOP" else "HOLD TO TALK",
                            color = if (isTransmitting) Color.White else Color(0xFF4285F4),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
        }
    }

    // ── Detail bottom sheet ──
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
            containerColor = OmniMeshColors.DarkSurface,
            dragHandle = null
        ) {
            TriageDetailSheet(
                packet = selected!!,
                onClose = { selected = null },
                viewModel = viewModel
            )
        }
    }

    if (showClaimDialog) {
        AlertDialog(
            onDismissRequest = { showClaimDialog = false },
            containerColor = Color(0xFF1C2025),
            title = {
                Text(
                    "CLAIM SECTOR",
                    color = Color(0xFF4285F4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Label this search sector at your current location",
                        color = Color(0xFF9AA0A6),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = claimLabel,
                        onValueChange = { claimLabel = it },
                        placeholder = { Text("e.g. Team Alpha — Block C") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedTextColor = Color(0xFFE8EAED),
                            unfocusedTextColor = Color(0xFFE8EAED),
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (claimLabel.isNotBlank()) {
                            val cameraPos = cameraPositionState.position.target
                            viewModel.claimSector(
                                lat = cameraPos.latitude,
                                lon = cameraPos.longitude,
                                label = claimLabel
                            )
                            showClaimDialog = false
                            claimLabel = ""
                        }
                    }
                ) {
                    Text("CLAIM", color = Color(0xFF4285F4), fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClaimDialog = false }) {
                    Text("CANCEL", color = Color(0xFF9AA0A6), fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

}

@Composable
private fun OnDeviceAnalysisStrip(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }
    val analysisScrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val analysisBodyMaxHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.36f).toInt().coerceIn(220, 420).dp
    }
    val infinite = rememberInfiniteTransition(label = "headerPulse")
    val headerDot by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headerDotAlpha"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = OmniMeshColors.DarkSurface,
        border = BorderStroke(1.dp, OmniMeshColors.MediumBlue.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer(alpha = headerDot)
                            .background(OmniMeshColors.MediumGreen, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ON-DEVICE AI ANALYSIS · COLLAPSE VERIFICATION",
                        color = OmniMeshColors.White,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSans
                    )
                }
                Text(
                    text = if (expanded) "▲ HIDE" else "▼ SHOW",
                    color = OmniMeshColors.MediumBlue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "LSTM audio + motion fusion + vision edge narrative",
                color = OmniMeshColors.Grey,
                fontSize = 11.sp,
                fontFamily = GoogleSans
            )
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = analysisBodyMaxHeight)
                        .verticalScroll(analysisScrollState)
                ) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AcousticBarsTile(modifier = Modifier.weight(1f))
                        MotionCopyTile(modifier = Modifier.weight(1f))
                        StructuralEdgeTile(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AcousticBarsTile(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "acousticBars")
    val confidence by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(animation = tween(2200), repeatMode = RepeatMode.Reverse),
        label = "acousticConf"
    )
    val iconPulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "acousticIcon"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = OmniMeshColors.DarkInk,
        border = BorderStroke(1.dp, OmniMeshColors.MediumRed.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎤", modifier = Modifier.graphicsLayer(alpha = iconPulse), fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "LSTM · AUDIO",
                    color = OmniMeshColors.MediumBlue,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(confidence * 100).toInt()}%",
                    color = OmniMeshColors.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            AcousticVisualizer(barColor = OmniMeshColors.MediumRed)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Low-frequency stress detected",
                color = OmniMeshColors.Yellow,
                fontSize = 12.sp,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Impact + scream signature model",
                color = OmniMeshColors.Grey,
                fontSize = 11.sp,
                fontFamily = GoogleSans
            )
        }
    }
}

@Composable
private fun MotionCopyTile(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "motionPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(850), repeatMode = RepeatMode.Reverse),
        label = "motionAlpha"
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1700, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "motionWave"
    )
    val iconPulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(760), repeatMode = RepeatMode.Reverse),
        label = "motionIcon"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = OmniMeshColors.DarkInk,
        border = BorderStroke(1.dp, OmniMeshColors.Yellow.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📈", modifier = Modifier.graphicsLayer(alpha = iconPulse), fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "LSTM · MOTION",
                    color = OmniMeshColors.Yellow.copy(alpha = alpha),
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(76 + (12f * kotlin.math.sin(phase * 0.5f))).toInt()}%",
                    color = OmniMeshColors.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                val baseY = size.height / 2f
                val w = size.width
                val pts = 30
                val palette = listOf(
                    OmniMeshColors.Yellow,
                    OmniMeshColors.Orange,
                    OmniMeshColors.MediumRed
                )
                for (line in 0..2) {
                    val path = androidx.compose.ui.graphics.Path()
                    for (i in 0..pts) {
                        val t = i / pts.toFloat()
                        val x = t * w
                        val y = baseY + sin(phase + t * 7f + line * 1.4f).toFloat() *
                                (size.height * 0.42f) *
                                (0.5f + 0.5f * sin(phase * 0.7f + line).toFloat())
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = palette[line].copy(alpha = 0.8f),
                        style = Stroke(width = 1.4f)
                    )
                }
            }
            Text(
                text = "Abnormal impact waveform",
                color = OmniMeshColors.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = GoogleSans
            )
        }
    }
}

@Composable
private fun StructuralEdgeTile(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "edgeScan")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1800), repeatMode = RepeatMode.Restart),
        label = "scanLine"
    )
    val flash by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "edgeFlash"
    )
    val iconPulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(820), repeatMode = RepeatMode.Reverse),
        label = "visionIcon"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = OmniMeshColors.DarkInk,
        border = BorderStroke(1.dp, OmniMeshColors.MediumBlue.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📷", modifier = Modifier.graphicsLayer(alpha = iconPulse), fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "VISION · EDGE",
                    color = OmniMeshColors.MediumBlue,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "VOID",
                    color = OmniMeshColors.MediumGreen.copy(alpha = flash),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                val edgeColor = OmniMeshColors.MediumBlue.copy(alpha = 0.75f)
                // Faux edge-detected building outline — looks like Sobel
                // output highlighting a structural void.
                val pts = listOf(
                    Offset(size.width * 0.04f, size.height * 0.85f),
                    Offset(size.width * 0.20f, size.height * 0.30f),
                    Offset(size.width * 0.34f, size.height * 0.55f),
                    Offset(size.width * 0.50f, size.height * 0.18f),
                    Offset(size.width * 0.66f, size.height * 0.52f),
                    Offset(size.width * 0.82f, size.height * 0.30f),
                    Offset(size.width * 0.96f, size.height * 0.65f),
                )
                for (i in 0 until pts.size - 1) {
                    drawLine(edgeColor, pts[i], pts[i + 1], 1.8f)
                }
                // Vertical reinforcement strokes — gives the SVG a sense
                // of building structure rather than just a polyline.
                listOf(0.20f, 0.50f, 0.82f).forEach { fx ->
                    drawLine(
                        OmniMeshColors.MediumBlue.copy(alpha = 0.35f),
                        Offset(size.width * fx, size.height * 0.95f),
                        Offset(size.width * fx, size.height * 0.30f),
                        1.0f
                    )
                }
                // Highlighted void rectangle pulses to emphasise the
                // collapsed area.
                drawRect(
                    color = OmniMeshColors.MediumGreen.copy(alpha = 0.18f * flash),
                    topLeft = Offset(size.width * 0.34f, size.height * 0.18f),
                    size = Size(size.width * 0.32f, size.height * 0.40f)
                )
                drawRect(
                    color = OmniMeshColors.MediumGreen.copy(alpha = 0.65f * flash),
                    topLeft = Offset(size.width * 0.34f, size.height * 0.18f),
                    size = Size(size.width * 0.32f, size.height * 0.40f),
                    style = Stroke(width = 1.2f)
                )
                // Active scanline.
                drawRect(
                    color = OmniMeshColors.MediumGreen.copy(alpha = 0.55f),
                    topLeft = Offset(0f, size.height * scan),
                    size = Size(size.width, 1.4f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Synthetic edge-detection · 3 voids",
                color = OmniMeshColors.Grey,
                fontSize = 10.sp,
                fontFamily = GoogleSans
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResponderHeader(sectorLabel: String, redCount: Int, autoCount: Int) {
    Surface(
        color = OmniMeshColors.DarkInk,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RESPONDER DASHBOARD: SECTOR $sectorLabel.",
                    color = OmniMeshColors.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = GoogleSans
                )
                Text(
                    text = "GPS: 28.6139°N, 77.2090°E",
                    color = OmniMeshColors.Grey,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            HeaderBadge(count = redCount, color = OmniMeshColors.MediumRed, label = "CRIT")
            Spacer(Modifier.size(6.dp))
            HeaderBadge(count = autoCount, color = OmniMeshColors.Yellow, label = "AUTO")
        }
    }
}

@Composable
private fun HeaderBadge(count: Int, color: Color, label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = count.toString(),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = label,
                color = color.copy(alpha = 0.8f),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontFamily = GoogleSans
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Map overlay label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapOverlayLabel(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "MESH MAP",
            color = OmniMeshColors.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            fontFamily = GoogleSans
        )
        Text(
            text = "Live triage positions · mesh routing",
            color = Color(0xFF9AA0A6),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Triage marker — custom triangle bitmap. RED markers get an animated
// pulsing radius via Circle composable above them.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TriageMarker(packet: TriagePacket, onClick: () -> Unit) {
    val density = LocalDensity.current
    val basePx = with(density) { 36.dp.toPx() }
    val color = urgencyColor(packet.urgency)
    val pulseScale = if (packet.urgency == "RED") {
        val infinite = rememberInfiniteTransition(label = "redPulse-${packet.id}")
        val p by infinite.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "markerPulse"
        )
        (p * 20).toInt() / 20f
    } else {
        1f
    }
    val sizePx = basePx * pulseScale
    val descriptor: BitmapDescriptor = remember(packet.urgency, pulseScale, packet.id) {
        triangleMarkerBitmap(sizePx.toInt().coerceAtLeast(24), color)
    }

    if (packet.urgency == "RED") {
        val infinite = rememberInfiniteTransition(label = "redHalo-${packet.id}")
        val pulse by infinite.animateFloat(
            initialValue = 25f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseRadius"
        )
        Circle(
            center = LatLng(packet.lat, packet.lon),
            radius = pulse.toDouble(),
            strokeColor = OmniMeshColors.MediumRed,
            strokeWidth = 1.5f,
            fillColor = OmniMeshColors.MediumRed.copy(alpha = 0.15f)
        )
    }

    val snippetBase = "Conf ${(packet.confidence * 100).toInt()}% · " +
        if (packet.isAutoGenerated) "AUTO" else "MANUAL"
    val snippetWithFloor = if (packet.loc.contains("Floor")) {
        "$snippetBase\n${packet.loc}"
    } else {
        snippetBase
    }

    Marker(
        state = MarkerState(position = LatLng(packet.lat, packet.lon)),
        title = "${packet.urgency} — ${packet.injury}",
        snippet = snippetWithFloor,
        icon = descriptor,
        zIndex = (5 - packet.urgencyPriority()).toFloat(),
        onClick = {
            onClick()
            true
        }
    )
}

/**
 * Renders a triangular alert pin as a Bitmap that the Maps SDK can display.
 * Uses the Android (not Compose) graphics pipeline because Marker icons
 * require a real BitmapDescriptor.
 */
private fun triangleMarkerBitmap(sizePx: Int, color: Color): BitmapDescriptor {
    val s = sizePx.coerceAtLeast(24)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        style = Paint.Style.FILL
    }
    val border = Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = s * 0.06f
        strokeJoin = Paint.Join.ROUND
    }
    val path = AndroidPath().apply {
        moveTo(s / 2f, s * 0.10f)
        lineTo(s * 0.95f, s * 0.85f)
        lineTo(s * 0.05f, s * 0.85f)
        close()
    }
    canvas.drawPath(path, paint)
    canvas.drawPath(path, border)

    // Exclamation mark inside the triangle.
    val textPaint = Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        textSize = s * 0.40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("!", s / 2f, s * 0.72f, textPaint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Triage queue card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TriageQueueCard(packet: TriagePacket, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, fg) = cardPalette(packet.urgency)
    val urgencyTint = urgencyColor(packet.urgency)
    val pulse = rememberInfiniteTransition(label = "redBorderPulse")
    val redBorderPulse by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "redBorderPulseValue"
    )
    val shimmerProgress = remember(packet.id) { Animatable(-0.6f) }
    var shimmerVisible by remember(packet.id) { mutableStateOf(true) }
    LaunchedEffect(packet.id) {
        shimmerVisible = true
        shimmerProgress.snapTo(-0.6f)
        shimmerProgress.animateTo(1.3f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        shimmerVisible = false
    }
    val sourceAbbrev = when {
        packet.signalSources.contains("MANUAL", ignoreCase = true) -> "MANUAL SOS"
        else -> packet.signalSources
            .replace("+", "·")
            .replace("MIC", "AUDIO")
            .replace("VISION", "VISION")
            .replace("MOTION", "MOTION")
    }

    Box(modifier = modifier.fillMaxWidth().heightIn(min = 68.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = bg,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "#${packet.id.take(6).uppercase()}",
                        color = fg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Text(
                        text = packet.injury.replace('_', ' '),
                        color = fg,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = GoogleSans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("GPS", color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(
                            "${"%.4f".format(packet.lat)}°, ${"%.4f".format(packet.lon)}°",
                            color = fg.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) {
                    Text(
                        text = "${(packet.confidence * 100).toInt()}%",
                        color = urgencyTint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = sourceAbbrev,
                        color = OmniMeshColors.Grey,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Detail",
                        tint = fg.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (packet.urgency == "RED") {
            val borderColor = Color(0xFFE53935).copy(alpha = 0.75f + 0.25f * redBorderPulse)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp, top = 2.dp, bottom = 2.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(borderColor, RoundedCornerShape(2.dp))
            )
        }

        if (shimmerVisible) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val x = shimmerProgress.value * size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        start = Offset(x - size.width * 0.25f, 0f),
                        end = Offset(x + size.width * 0.25f, size.height)
                    )
                )
            }
        }
    }
}

@Composable
private fun HeartbeatMonitorLine(eventKey: Int, eventAmplitude: Float) {
    val scroll = rememberInfiniteTransition(label = "heartbeatScroll")
    val phase by scroll.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heartbeatPhase"
    )
    val spikes = remember { mutableStateListOf<Pair<Float, Float>>() } // position, amp

    LaunchedEffect(eventKey) {
        if (eventKey > 0) spikes.add(0.96f to eventAmplitude)
    }
    LaunchedEffect(Unit) {
        while (true) {
            for (i in spikes.indices.reversed()) {
                val (pos, amp) = spikes[i]
                val moved = pos - 0.028f
                if (moved < -0.08f) spikes.removeAt(i) else spikes[i] = moved to amp
            }
            delay(55)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF12161C), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF26303A), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val mid = size.height * 0.58f
        val ampBase = size.height * 0.10f
        val step = size.width / 90f
        var x = 0f
        var last = Offset(0f, mid)

        while (x <= size.width) {
            val t = x / size.width
            var y = mid + sin((t * 15f + phase * 14f).toDouble()).toFloat() * ampBase
            spikes.forEach { (spikePos, spikeAmp) ->
                val dx = t - spikePos
                val gaussian = kotlin.math.exp(-((dx * dx) / 0.0009)).toFloat()
                y -= gaussian * (size.height * 0.42f) * spikeAmp
            }
            val point = Offset(x, y)
            drawLine(
                color = Color(0xFF34A853),
                start = last,
                end = point,
                strokeWidth = 1.6.dp.toPx()
            )
            last = point
            x += step
        }
    }
}

@Composable
private fun SonarSweep(center: LatLng) {
    val sweep = rememberInfiniteTransition(label = "sonarSweep")
    val progress by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonarSweepProgress"
    )
    Circle(
        center = center,
        radius = (25f + progress * 280f).toDouble(),
        fillColor = Color(0xFF34A853).copy(alpha = (0.20f * (1f - progress)).coerceAtLeast(0f)),
        strokeColor = Color(0xFF34A853).copy(alpha = (0.7f * (1f - progress)).coerceAtLeast(0f)),
        strokeWidth = 2.5f
    )
}

@Composable
private fun AnimatedWaypointTrail(
    waypoints: List<LatLng>,
    visibleCount: Int
) {
    if (waypoints.isEmpty() || visibleCount <= 0) return
    val pulse = rememberInfiniteTransition(label = "waypointPulse")
    val pop by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waypointPop"
    )
    waypoints.take(visibleCount.coerceAtMost(waypoints.size)).forEachIndexed { idx, wp ->
        val isNewest = idx == visibleCount - 1
        Circle(
            center = wp,
            radius = if (isNewest) (10f * pop).toDouble() else 7.0,
            fillColor = Color(0xFF8AB4F8).copy(alpha = if (isNewest) 0.55f else 0.35f),
            strokeColor = Color(0xFF4285F4).copy(alpha = if (isNewest) 0.95f else 0.55f),
            strokeWidth = if (isNewest) 2.3f else 1.5f
        )
    }
}

private fun cardPalette(urgency: String): Pair<Color, Color> = when (urgency) {
    "RED" -> OmniMeshColors.MediumRed to OmniMeshColors.White
    // Dark amber surface so foreground stays light (matches translucent yellow-on-dark web cards).
    "YELLOW" -> Color(0xFF8B6914) to OmniMeshColors.White
    "GREEN" -> OmniMeshColors.MediumGreen to OmniMeshColors.White
    else -> OmniMeshColors.GreyDeceased to OmniMeshColors.White
}

// ─────────────────────────────────────────────────────────────────────────────
// Proximity alerts footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProximityAlertsFooter(autoCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = OmniMeshColors.Red
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = OmniMeshColors.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Proximity Alerts: $autoCount Critically affected.",
                color = OmniMeshColors.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSans
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TriageDetailSheet(
    packet: TriagePacket,
    onClose: () -> Unit,
    viewModel: OmniMeshViewModel
) {
    val accent = urgencyColor(packet.urgency)
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQr by remember { mutableStateOf(false) }

    LaunchedEffect(packet.id) {
        viewModel.generateQrForPacket(packet).collect { bitmap ->
            qrBitmap = bitmap
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = accent.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
            ) {
                Text(
                    text = packet.urgency,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontFamily = GoogleSans,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = OmniMeshColors.Grey
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = packet.injury.replace('_', ' '),
            color = OmniMeshColors.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSans
        )
        if (packet.loc.contains("Floor")) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = packet.loc,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFBBC04)
            )
        }
        Spacer(Modifier.height(16.dp))
        DetailRow("VICTIM ID", "#${packet.id}")
        DetailRow("GPS", "${"%.5f".format(packet.lat)}, ${"%.5f".format(packet.lon)}")
        DetailRow("LOCATION", packet.loc)
        DetailRow("CONFIDENCE", "${(packet.confidence * 100).toInt()}%")
        DetailRow("SIGNAL SOURCES", packet.signalSources.ifBlank { "—" })
        DetailRow("HOPS", packet.hopCount.toString())
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showQr = !showQr },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.Filled.QrCode,
                contentDescription = null,
                tint = Color(0xFF4285F4),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (showQr) "HIDE TRIAGE QR" else "SHOW TRIAGE QR CARD",
                color = Color(0xFF4285F4),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        AnimatedVisibility(visible = showQr && qrBitmap != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Triage QR Code",
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "SCAN TO ACCESS VICTIM RECORD",
                    color = Color(0xFF9AA0A6),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    "ID: ${packet.id}",
                    color = Color(0xFF5F6368),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        DataQualityDetail(
            packet = packet,
            onReached = { viewModel.markResponderReached(packet.id) },
            onConfirm = { viewModel.markPacketConfirmed(packet.id) },
            onFalsePositive = { viewModel.markPacketFalsePositive(packet.id) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (packet.isAutoGenerated) OmniMeshColors.Yellow.copy(alpha = 0.15f)
            else OmniMeshColors.MediumBlue.copy(alpha = 0.15f),
            border = BorderStroke(
                1.dp,
                if (packet.isAutoGenerated) OmniMeshColors.Yellow.copy(alpha = 0.5f)
                else OmniMeshColors.MediumBlue.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = if (packet.isAutoGenerated) "AUTO-DETECTED" else "MANUAL REPORT",
                color = if (packet.isAutoGenerated) OmniMeshColors.Yellow else OmniMeshColors.MediumBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                fontFamily = GoogleSans,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = OmniMeshColors.Grey,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            fontFamily = GoogleSans,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = OmniMeshColors.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

