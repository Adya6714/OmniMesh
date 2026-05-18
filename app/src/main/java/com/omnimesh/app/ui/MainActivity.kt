package omnimesh.command1.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import omnimesh.command1.OmniMeshApp as OmniMeshApplication
import omnimesh.command1.disaster.DisasterType
import omnimesh.command1.service.CollapseDetectorService
import omnimesh.command1.service.MeshRelayService
import omnimesh.command1.data.TriagePacket
import omnimesh.command1.ui.theme.OmniMeshColors
import omnimesh.command1.ui.theme.OmniMeshTheme
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: OmniMeshViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startOmniMeshServices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startOmniMeshServices()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            // The Victim screen is light, Responder/Command are dark. The
            // global theme stays light because each dark screen paints its
            // own surfaces — this gives the dark screens an explicit
            // tactical feel without forcing system widgets to flip.
            OmniMeshTheme(darkTheme = false) {
                OmniMeshApp(viewModel)
            }
        }
    }

    private fun startOmniMeshServices() {
        startForegroundService(Intent(this, MeshRelayService::class.java))
        startForegroundService(Intent(this, CollapseDetectorService::class.java))
    }
}

@Composable
fun OmniMeshApp(viewModel: OmniMeshViewModel) {
    val mode by viewModel.mode.collectAsState()
    val sosState by viewModel.sosState.collectAsState()
    val isBeaconActive by viewModel.isBeaconActive.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()
    val dispatchResult by viewModel.dispatchResult.collectAsState()
    val packets by viewModel.packets.observeAsState(initial = emptyList())
    val meshApplication = LocalContext.current.applicationContext as OmniMeshApplication
    val disasterType by meshApplication.disasterStateManager.currentDisasterType.collectAsStateWithLifecycle(
        initialValue = DisasterType.NORMAL
    )
    var demoMode by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    var cascadeAlert by remember { mutableStateOf<TriagePacket?>(null) }
    var knownRedIds by remember { mutableStateOf(setOf<String>()) }
    val uiPackets = remember(demoMode, packets) { if (demoMode) demoPackets() else packets }

    val meshEndpoints by meshApplication.nearbyMeshManager.connectedEndpoints.collectAsState()
    LaunchedEffect(meshEndpoints) {
        viewModel.updatePeerCount(meshEndpoints.size)
    }

    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
    }
    LaunchedEffect(uiPackets) {
        val currentRed = uiPackets.filter { it.urgency == "RED" }
        val currentIds = currentRed.map { it.id }.toSet()
        if (knownRedIds.isNotEmpty()) {
            val newlyArrived = currentRed.firstOrNull { it.id !in knownRedIds }
            if (newlyArrived != null) {
                cascadeAlert = newlyArrived
                delay(2500)
                cascadeAlert = null
            }
        }
        knownRedIds = currentIds
    }

    if (showSplash) {
        OmniMeshSplash()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val anyRed = uiPackets.any { it.urgency == "RED" }
        val anyYellow = uiPackets.any { it.urgency == "YELLOW" }
        val disasterWarning = disasterType != DisasterType.NORMAL
        val autoSosPulse = isBeaconActive ||
            sosState == SOSState.RECORDING ||
            sosState == SOSState.TRIGGERED ||
            sosState == SOSState.TRANSMITTED
        Column(Modifier.fillMaxSize()) {
            SystemStatusStrip(
                redPulse = anyRed || autoSosPulse,
                yellowBar = !anyRed && !autoSosPulse && (anyYellow || disasterWarning),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                when (mode) {
                    AppMode.VICTIM -> VictimScreen(
                        viewModel = viewModel,
                        sosState = sosState,
                        isRecording = isRecording,
                        peerCount = peerCount,
                        demoMode = demoMode,
                        onToggleDemoMode = { demoMode = !demoMode },
                        onSOSTap = { request -> viewModel.triggerManualSOS(request) },
                        onVoiceSOS = { viewModel.triggerVoiceSOS() },
                        onSwitchToResponder = { viewModel.setMode(AppMode.RESPONDER) }
                    )
                    AppMode.RESPONDER -> ResponderScreen(
                        packets = uiPackets,
                        viewModel = viewModel,
                        demoMode = demoMode,
                        onToggleDemoMode = { demoMode = !demoMode }
                    )
                    AppMode.COMMAND -> CommandScreen(
                        viewModel = viewModel,
                        packets = uiPackets,
                        dispatchResult = dispatchResult,
                        demoMode = demoMode,
                        onToggleDemoMode = { demoMode = !demoMode },
                        onRunAgent = { viewModel.runDispatchAgent() },
                        onSpeakDispatch = { /* TTS hook — left to caller / DispatchTTSManager */ }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .zIndex(8f)
            ) {
                OmniMeshBottomNav(
                    mode = mode,
                    hasCriticalAlerts = uiPackets.any { it.urgency == "RED" },
                    demoMode = demoMode,
                    onToggleDemoMode = { demoMode = !demoMode },
                    onSelect = viewModel::setMode
                )
            }
        }
        AlertCascadeBanner(
            packet = cascadeAlert,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                .zIndex(12f)
        )
    }
}

/**
 * Bottom navigation. Active tab uses an underline-style indicator in the
 * mode-specific accent color: Victim red, Responder yellow, Command blue.
 * The Alerts tab carries a dot badge whenever any RED packets exist.
 */
@Composable
private fun OmniMeshBottomNav(
    mode: AppMode,
    hasCriticalAlerts: Boolean,
    demoMode: Boolean,
    onToggleDemoMode: () -> Unit,
    onSelect: (AppMode) -> Unit
) {
    val tabs = listOf(
        BottomTab.Victim,
        BottomTab.Responder,
        BottomTab.Command
    )
    val currentTab = mode.toBottomTab()

    val navBg = OmniMeshColors.DarkBackground
    val navBorder = Color(0xFF2C2C2E)
    val unselectedTint = Color(0xFF5F6368)

    Surface(
        color = navBg,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .border(0.5.dp, navBorder)
    ) {
        Box(Modifier.fillMaxSize()) {
            TextButton(
                onClick = onToggleDemoMode,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 2.dp)
            ) {
                Text(
                    text = "DATA",
                    color = if (demoMode) Color(0xFFFBBC04) else Color(0xFF9AA0A6),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                tabs.forEach { tab ->
                    val selected = tab == currentTab
                    val activeColor = tab.tabColor
                    val bounce by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
                        label = "navBounce-${tab.label}"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .scale(1f + 0.08f * bounce)
                            .clickable { onSelect(tab.toAppMode()) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(2.dp)
                                .background(
                                    if (selected) activeColor else Color.Transparent,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (selected) activeColor else unselectedTint
                            )
                            if (tab == BottomTab.Victim && hasCriticalAlerts) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = 1.dp)
                                        .size(7.dp)
                                        .background(Color(0xFFEA4335), CircleShape)
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            color = if (selected) activeColor else unselectedTint,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemStatusStrip(
    redPulse: Boolean,
    yellowBar: Boolean,
) {
    val flash = rememberInfiniteTransition(label = "statusFlash")
    val flashAlpha by flash.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusFlashAlpha"
    )
    val color = when {
        redPulse -> Color(0xFFEA4335).copy(alpha = flashAlpha)
        yellowBar -> Color(0xFFFBBC04)
        else -> Color(0xFF4285F4)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(color)
    )
}

@Composable
private fun AlertCascadeBanner(packet: TriagePacket?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = packet != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(180)),
        modifier = modifier
    ) {
        packet?.let { p ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF111418),
                border = BorderStroke(1.dp, Color(0xFFEA4335).copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "RED",
                        color = Color(0xFFEA4335),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column {
                        Text(
                            text = p.injury.take(42).replace('_', ' '),
                            color = Color(0xFFE8EAED),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "GPS ${"%.4f".format(p.lat)}, ${"%.4f".format(p.lon)}",
                            color = Color(0xFF9AA0A6),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OmniMeshSplash() {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (t < 1f) {
            delay(16)
            t += 0.008f
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B10)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nodeCount = 32
            repeat(nodeCount) { i ->
                val p = ((t * 1.2f) - (i * 0.02f)).coerceIn(0f, 1f)
                val edge = when (i % 4) {
                    0 -> Offset(0f, size.height * (i / nodeCount.toFloat()))
                    1 -> Offset(size.width, size.height * (i / nodeCount.toFloat()))
                    2 -> Offset(size.width * (i / nodeCount.toFloat()), 0f)
                    else -> Offset(size.width * (i / nodeCount.toFloat()), size.height)
                }
                val target = Offset(
                    x = size.width * (0.25f + ((i * 37) % 100) / 200f),
                    y = size.height * (0.25f + ((i * 53) % 100) / 200f)
                )
                val x = edge.x + (target.x - edge.x) * p
                val y = edge.y + (target.y - edge.y) * p
                drawCircle(Color(0xFF4285F4).copy(alpha = 0.22f + p * 0.4f), radius = 2f + p, center = Offset(x, y))
            }
        }
        Canvas(modifier = Modifier.size(220.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val ringGrow = (t * 1.2f).coerceIn(0f, 1f)
            val radius = size.minDimension * 0.42f * ringGrow
            val spin = t * 900f
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = spin,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            val drawProgress = (t * 1.3f).coerceIn(0f, 1f)
            val m = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.64f)
                lineTo(size.width * 0.40f, size.height * 0.36f)
                lineTo(size.width * 0.50f, size.height * 0.56f)
                lineTo(size.width * 0.60f, size.height * 0.36f)
                lineTo(size.width * 0.70f, size.height * 0.64f)
            }
            drawPath(
                path = m,
                color = Color(0xFF8AB4F8),
                style = Stroke(width = 6f * drawProgress, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "OMNIMESH",
            color = Color(0xFFE8EAED).copy(alpha = (t * 1.5f).coerceIn(0f, 1f)),
            fontSize = 20.sp,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset(y = 120.dp)
        )
    }
}

private fun demoPackets() = listOf(
    omnimesh.command1.data.TriagePacket(
        id = "demo-r1",
        urgency = "RED",
        injury = "Unconscious — building collapse",
        loc = "Sector 4",
        lat = 28.3694,
        lon = 75.3651,
        ts = System.currentTimeMillis(),
        confidence = 0.94f,
        signalSources = "MIC+MOTION+VISION",
        isAutoGenerated = true
    ),
    omnimesh.command1.data.TriagePacket(
        id = "demo-r2",
        urgency = "RED",
        injury = "Severe crush injury",
        loc = "Sector 4",
        lat = 28.3695,
        lon = 75.3653,
        ts = System.currentTimeMillis(),
        confidence = 0.91f,
        signalSources = "MANUAL SOS",
        isAutoGenerated = false
    ),
    omnimesh.command1.data.TriagePacket(
        id = "demo-y1",
        urgency = "YELLOW",
        injury = "Compound fracture — stable",
        loc = "Sector 5",
        lat = 28.3696,
        lon = 75.3657,
        ts = System.currentTimeMillis(),
        confidence = 0.78f,
        signalSources = "MONO+AUDIO",
        isAutoGenerated = true
    ),
    omnimesh.command1.data.TriagePacket(
        id = "demo-g1",
        urgency = "GREEN",
        injury = "Walking wounded",
        loc = "Sector 5",
        lat = 28.3701,
        lon = 75.3660,
        ts = System.currentTimeMillis(),
        confidence = 0.62f,
        signalSources = "MANUAL",
        isAutoGenerated = false
    )
)

/**
 * Top-level navigation tabs. They map to the existing AppMode enum; this
 * keeps the four-icon mockup nav while preserving the three-mode
 * ViewModel API. "Map" and "Triage" both surface the Responder screen,
 * which contains both views in one layout.
 */
private enum class BottomTab(
    val label: String,
    val icon: ImageVector,
    val tabColor: Color
) {
    Victim("VICTIM", Icons.Filled.Home, Color(0xFFEA4335)),
    Responder("RESPONDER", Icons.Filled.Map, Color(0xFFFBBC04)),
    Command("COMMAND", Icons.Filled.Dashboard, Color(0xFF4285F4))
}

private fun BottomTab.toAppMode(): AppMode = when (this) {
    BottomTab.Victim -> AppMode.VICTIM
    BottomTab.Responder -> AppMode.RESPONDER
    BottomTab.Command -> AppMode.COMMAND
}

private fun AppMode.toBottomTab(): BottomTab = when (this) {
    AppMode.VICTIM -> BottomTab.Victim
    AppMode.RESPONDER -> BottomTab.Responder
    AppMode.COMMAND -> BottomTab.Command
}

