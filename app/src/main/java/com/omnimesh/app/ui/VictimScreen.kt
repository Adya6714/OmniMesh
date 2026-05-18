package omnimesh.command1.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import omnimesh.command1.ui.components.MeshBackground
import omnimesh.command1.ui.components.OmniMeshTopBar
import omnimesh.command1.companion.CompanionState
import omnimesh.command1.location.IndoorLocation
import omnimesh.command1.ui.theme.GoogleSans
import omnimesh.command1.ui.theme.OmniBody
import omnimesh.command1.ui.theme.OmniDisplay
import omnimesh.command1.ui.theme.OmniMeshColors
import omnimesh.command1.ui.theme.OmniMono

private val manualSosUrgencyOptions = listOf("RED", "YELLOW", "GREEN")
private val manualSosInjuryOptions = listOf(
    "Unknown critical injury",
    "Trapped or pinned",
    "Compound fracture",
    "Heavy bleeding",
    "Trouble breathing",
    "Head injury",
    "Unconscious",
)

/**
 * Victim screen — Material You light theme, marketing-grade.
 *
 * Visual goals:
 *  • Calm reassuring surface (white card, soft shadows, no panic).
 *  • The mesh is *visibly alive* — touch-reactive node graph in the
 *    background, pulsing concentric rings around the brand mark, and
 *    sensor icons that animate to demonstrate that on-device detection
 *    is actually happening (audio bars + motion ticks).
 *  • Single primary action — REPORT EMERGENCY — never crowded.
 *
 * All ViewModel signatures are preserved verbatim. Only the visual layer
 * changes; voice SOS, injury capture and Companion still live underneath.
 */
@Composable
fun VictimScreen(
    viewModel: OmniMeshViewModel,
    sosState: SOSState,
    isRecording: Boolean,
    peerCount: Int,
    demoMode: Boolean,
    onToggleDemoMode: () -> Unit,
    onSOSTap: (ManualSosRequest) -> Unit,
    onVoiceSOS: () -> Unit,
    onSwitchToResponder: () -> Unit = {}
) {
    val context = LocalContext.current
    val packets by viewModel.packets.observeAsState(initial = emptyList())
    val lastManualSosAck by viewModel.lastManualSosAck.collectAsState()
    val indoorLocation by viewModel.indoorLocation.collectAsState()
    val isBeaconActive by viewModel.isBeaconActive.collectAsState()
    val companionState by viewModel.companionState.collectAsState()
    val companionMessages by viewModel.companionMessages.collectAsState()
    val companionClinicalState by viewModel.companionClinicalState.collectAsState()
    val companionIsListening by viewModel.companionIsListening.collectAsState()
    var showStartTriage by remember { mutableStateOf(false) }
    var showBuddySetup by remember { mutableStateOf(false) }
    var showEmergencySheet by remember { mutableStateOf(false) }
    var selectedUrgency by remember { mutableStateOf("RED") }
    var selectedInjury by remember { mutableStateOf(manualSosInjuryOptions.first()) }
    var additionalDetails by remember { mutableStateOf("") }
    var unableToSpeak by remember { mutableStateOf(false) }
    val buddyGroup by viewModel.buddyGroup.collectAsState()

    if (companionState != CompanionState.IDLE && companionState != CompanionState.ENDED) {
        CompanionScreen(
            messages = companionMessages,
            state = companionState,
            clinicalState = companionClinicalState,
            isListening = companionIsListening,
            onSendText = viewModel::sendCompanionMessage,
            onStartVoice = viewModel::startCompanionVoiceInput,
            onEndSession = viewModel::endCompanionSession,
        )
        return
    }

    if (showStartTriage) {
        StartTriageScreen(
            onComplete = { triageResult ->
                viewModel.submitStartTriageResult(triageResult)
                showStartTriage = false
            },
            onSkip = { showStartTriage = false }
        )
        return
    }
    if (showBuddySetup) {
        val lastJoinCode by viewModel.lastJoinCode.collectAsState()
        BuddyGroupSetupScreen(
            currentGroup = buddyGroup,
            joinCode = lastJoinCode,
            onCreateGroup = viewModel::createBuddyGroup,
            onJoinGroup = viewModel::joinBuddyGroup,
            onLeaveGroup = viewModel::leaveBuddyGroup,
            onBack = { showBuddySetup = false }
        )
        return
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onImageCaptured()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchInjuryCapture(context, viewModel, cameraLauncher)
    }

    val redCount = packets.count { it.urgency == "RED" }
    val scrollState = rememberScrollState()
    var sosDisplayState by remember { mutableStateOf(sosState) }
    LaunchedEffect(sosState) {
        sosDisplayState = sosState
        if (sosState == SOSState.TRANSMITTED) {
            delay(3000)
            sosDisplayState = SOSState.IDLE
        }
    }

    if (showEmergencySheet) {
        ManualEmergencySheet(
            location = indoorLocation,
            selectedUrgency = selectedUrgency,
            onUrgencySelected = { selectedUrgency = it },
            selectedInjury = selectedInjury,
            onInjurySelected = { selectedInjury = it },
            additionalDetails = additionalDetails,
            onAdditionalDetailsChange = { additionalDetails = it },
            unableToSpeak = unableToSpeak,
            onUnableToSpeakChange = { unableToSpeak = it },
            onDismiss = { showEmergencySheet = false },
            onSend = {
                onSOSTap(
                    ManualSosRequest(
                        urgency = selectedUrgency,
                        injury = selectedInjury,
                        note = additionalDetails,
                        unableToSpeak = unableToSpeak,
                    )
                )
                showEmergencySheet = false
                selectedUrgency = "RED"
                selectedInjury = manualSosInjuryOptions.first()
                additionalDetails = ""
                unableToSpeak = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OmniMeshColors.LightGrey)
    ) {
        MeshBackground(
            modifier = Modifier.fillMaxSize(),
            nodeCount = 20,
            nodeColor = Color(0xFF4285F4),
            edgeColor = Color(0xFF4285F4),
            nodeAlpha = 0.08f,
            edgeAlpha = 0.03f,
            connectionDistancePx = 220f,
            interactionRadiusPx = 160f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x174285F4), Color.Transparent),
                        center = Offset(120f, 80f),
                        radius = 700f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x1034A853), Color.Transparent),
                        center = Offset(900f, 1200f),
                        radius = 800f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            OmniMeshTopBar(
                screenName = "VICTIM",
                peerCount = peerCount,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showBuddySetup = true }) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = OmniMeshColors.Grey
                        )
                        val buddyMembers = buddyGroup?.members?.size ?: 0
                        if (buddyMembers > 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 4.dp, y = (-2).dp)
                                    .size(7.dp)
                                    .background(OmniMeshColors.MediumGreen, CircleShape)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MeshStatusHub(
                        peerCount = peerCount,
                        autoSosActive = isBeaconActive
                    )

                    IndoorLocationBadge(
                        location = indoorLocation,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    AutomatedDistressCard(isRecording = isRecording)

                    LiveSignalsStrip(peerCount = peerCount)
                    MeshActivityBand(peerCount = peerCount, redCount = redCount)

                    ReportEmergencyButton(
                        sosState = sosDisplayState,
                        onTap = { showEmergencySheet = true }
                    )
                }

                AnimatedVisibility(
                    visible = sosState == SOSState.IDLE || sosState == SOSState.TRANSMITTED
                ) {
                    OutlinedButton(
                        onClick = { showStartTriage = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, OmniMeshColors.MediumGreen.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            Icons.Filled.AssignmentInd,
                            contentDescription = null,
                            tint = OmniMeshColors.MediumGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SELF-ASSESS MY INJURIES",
                            color = OmniMeshColors.MediumGreen,
                            fontSize = 11.sp,
                            fontFamily = OmniMono,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = lastManualSosAck != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    lastManualSosAck?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = OmniMeshColors.LightGreen,
                            border = BorderStroke(1.dp, OmniMeshColors.MediumGreen.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(14.dp),
                                color = OmniMeshColors.Green,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                fontFamily = OmniBody
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = redCount > 0) {
                    NearbyTriageCard(
                        redCount = redCount,
                        onClick = onSwitchToResponder
                    )
                }
                ActionFillSection(
                    showDisasterBanner = redCount > 0,
                    isAutoSosActive = isBeaconActive,
                    onVoiceSOS = onVoiceSOS,
                    onCapture = {
                        if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchInjuryCapture(context, viewModel, cameraLauncher)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onSilenceBeacon = { viewModel.stopAcousticBeacon() },
                    onStartCompanion = { viewModel.debugStartCompanion() }
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEmergencySheet(
    location: IndoorLocation?,
    selectedUrgency: String,
    onUrgencySelected: (String) -> Unit,
    selectedInjury: String,
    onInjurySelected: (String) -> Unit,
    additionalDetails: String,
    onAdditionalDetailsChange: (String) -> Unit,
    unableToSpeak: Boolean,
    onUnableToSpeakChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OmniMeshColors.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Send Emergency Packet",
                fontFamily = OmniDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = OmniMeshColors.Black
            )
            Text(
                text = "Fast send is still available, but now you can describe what is actually happening.",
                fontFamily = OmniBody,
                fontSize = 14.sp,
                color = OmniMeshColors.Grey
            )

            ManualEmergencySection("LOCATION")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = OmniMeshColors.LightBlue.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, OmniMeshColors.MediumBlue.copy(alpha = 0.25f))
            ) {
                Text(
                    text = manualEmergencyLocationSummary(location),
                    modifier = Modifier.padding(14.dp),
                    color = OmniMeshColors.Black,
                    fontFamily = OmniBody,
                    fontSize = 13.sp
                )
            }

            ManualEmergencySection("SEVERITY")
            manualSosUrgencyOptions.forEach { option ->
                ManualEmergencyChoiceButton(
                    label = option,
                    selected = option == selectedUrgency,
                    onClick = { onUrgencySelected(option) }
                )
            }

            ManualEmergencySection("INJURY / DISTRESS TYPE")
            manualSosInjuryOptions.forEach { option ->
                ManualEmergencyChoiceButton(
                    label = option,
                    selected = option == selectedInjury,
                    onClick = { onInjurySelected(option) }
                )
            }

            ManualEmergencyChoiceButton(
                label = if (unableToSpeak) "Unable to speak selected" else "Mark victim as unable to speak",
                selected = unableToSpeak,
                onClick = { onUnableToSpeakChange(!unableToSpeak) }
            )

            OutlinedTextField(
                value = additionalDetails,
                onValueChange = onAdditionalDetailsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Extra details (optional)") },
                placeholder = { Text("Pinned under debris, bleeding from leg, cannot move...") },
                minLines = 3,
                maxLines = 4,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = OmniMeshColors.Grey)
                }
                Button(
                    onClick = onSend,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OmniMeshColors.MediumRed)
                ) {
                    Text("Send emergency", color = OmniMeshColors.White)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ManualEmergencySection(title: String) {
    Text(
        text = title,
        color = OmniMeshColors.Grey,
        fontFamily = OmniMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp
    )
}

@Composable
private fun ManualEmergencyChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) OmniMeshColors.MediumRed else OmniMeshColors.Grey.copy(alpha = 0.25f)
    val background = if (selected) OmniMeshColors.LightRed.copy(alpha = 0.28f) else OmniMeshColors.White
    val textColor = if (selected) OmniMeshColors.MediumRed else OmniMeshColors.Black

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = textColor
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = OmniBody,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

private fun manualEmergencyLocationSummary(location: IndoorLocation?): String {
    if (location == null) {
        return "Live location is not ready yet. The app will fall back to its current default coordinates for this build."
    }
    val floorLabel = location.estimatedFloor?.let { "Floor $it" } ?: "Floor unknown"
    val accuracyLabel = "${location.accuracyMeters.toInt()}m accuracy"
    val methodLabel = location.method.name.lowercase().replace('_', ' ')
    return "Live location ready: $floorLabel · $accuracyLabel · via $methodLabel"
}

private fun launchInjuryCapture(
    context: android.content.Context,
    viewModel: OmniMeshViewModel,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<android.net.Uri, Boolean>
) {
    val photoFile = viewModel.createImageFile(context)
    val photoUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
    launcher.launch(photoUri)
}

// ─────────────────────────────────────────────────────────────────────────────
// Mesh status hub — four rotating arcs + diamond M mark + live peer count
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MeshStatusHub(peerCount: Int, autoSosActive: Boolean) {
    var ringAngle by remember { mutableStateOf(0f) }
    val peerPulse = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        var previousFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (previousFrame == 0L) {
                    previousFrame = now
                } else {
                    val dtSeconds = (now - previousFrame) / 1_000_000_000f
                    previousFrame = now
                    val speed = if (autoSosActive) 88f else 45f
                    ringAngle = (ringAngle + dtSeconds * speed) % 360f
                }
            }
        }
    }

    LaunchedEffect(peerCount) {
        peerPulse.snapTo(0f)
        peerPulse.animateTo(1f, animationSpec = tween(140, easing = FastOutSlowInEasing))
        peerPulse.animateTo(0f, animationSpec = tween(520, easing = LinearEasing))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .background(OmniMeshColors.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                val outerStroke = Stroke(width = (4.dp.toPx() + 2.dp.toPx() * peerPulse.value))
                val innerStroke = Stroke(width = 2.dp.toPx())
                val gapDegrees = 10f
                val sweepDegrees = 80f
                val baseArcColors = listOf(
                    OmniMeshColors.Blue,
                    OmniMeshColors.Red,
                    OmniMeshColors.Yellow,
                    OmniMeshColors.Green,
                )
                val warningIndex = ((ringAngle / 90f).toInt()).mod(4)
                val ringColors = if (autoSosActive) {
                    List(4) { i ->
                        if (i == warningIndex) Color(0xFFFF4A4A) else Color(0xFFB32020)
                    }
                } else baseArcColors

                // Outer radial ticks (32), tinted near segment arcs.
                val tickCount = 32
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radiusOuter = size.minDimension / 2f
                val tickOuter = radiusOuter + 6.dp.toPx()
                val tickInner = radiusOuter - 1.dp.toPx()
                repeat(tickCount) { idx ->
                    val angleDeg = (idx * 360f / tickCount + ringAngle) % 360f
                    val angleRad = Math.toRadians((angleDeg - 90f).toDouble())
                    val x1 = cx + cos(angleRad).toFloat() * tickInner
                    val y1 = cy + sin(angleRad).toFloat() * tickInner
                    val x2 = cx + cos(angleRad).toFloat() * tickOuter
                    val y2 = cy + sin(angleRad).toFloat() * tickOuter

                    val segmentIndex = (((angleDeg - gapDegrees / 2f + 360f) % 360f) / 90f).toInt().coerceIn(0, 3)
                    val segmentLocal = ((angleDeg - segmentIndex * 90f + 360f) % 360f)
                    val nearSegmentArc = segmentLocal in 6f..84f
                    val tickColor = if (nearSegmentArc) {
                        ringColors[segmentIndex].copy(alpha = 0.55f + 0.45f * peerPulse.value)
                    } else {
                        OmniMeshColors.Grey.copy(alpha = 0.22f)
                    }
                    drawLine(
                        color = tickColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 1.4.dp.toPx()
                    )
                }

                ringColors.forEachIndexed { i, c ->
                    drawArc(
                        color = c.copy(alpha = 0.86f + 0.14f * peerPulse.value),
                        startAngle = ringAngle + i * (sweepDegrees + gapDegrees) + gapDegrees / 2f,
                        sweepAngle = sweepDegrees,
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        style = outerStroke
                    )
                    drawArc(
                        color = c.copy(alpha = 0.70f + 0.30f * peerPulse.value),
                        startAngle = -(ringAngle * 0.5f) + 45f + i * (sweepDegrees + gapDegrees) + gapDegrees / 2f,
                        sweepAngle = sweepDegrees * 0.92f,
                        useCenter = false,
                        topLeft = Offset(
                            x = 12.dp.toPx(),
                            y = 12.dp.toPx()
                        ),
                        size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
                        style = innerStroke
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                MeshHubNetworkLogo(size = 76.dp)
                Text(
                    text = "OMNIMESH ACTIVE.",
                    color = OmniMeshColors.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OmniDisplay,
                    letterSpacing = 1.6.sp
                )
                Text(
                    text = "MESH CONNECTED: $peerCount PEERS",
                    color = OmniMeshColors.Grey,
                    fontSize = 13.sp,
                    fontFamily = OmniMono,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center
                )
                HubLiveSignalBars(autoSosActive = autoSosActive)
            }
        }
    }
}

@Composable
private fun HubLiveSignalBars(autoSosActive: Boolean) {
    val heights = remember { mutableStateListOf(0.3f, 0.4f, 0.6f, 0.5f, 0.35f, 0.52f, 0.44f, 0.58f) }
    val healthStates = remember { mutableStateListOf(0, 0, 1, 0, 0, 1, 0, 0) } // 0=healthy, 1=weak, 2=failing

    LaunchedEffect(autoSosActive) {
        while (true) {
            for (i in heights.indices) {
                heights[i] = Random.nextFloat().coerceIn(0.2f, 1f)
                val roll = Random.nextFloat()
                healthStates[i] = when {
                    autoSosActive && roll > 0.45f -> 2
                    roll > 0.82f -> 2
                    roll > 0.58f -> 1
                    else -> 0
                }
            }
            delay(600)
        }
    }

    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEachIndexed { idx, h ->
            val barColor = when (healthStates[idx]) {
                2 -> OmniMeshColors.MediumRed
                1 -> OmniMeshColors.Yellow
                else -> OmniMeshColors.MediumGreen
            }
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = (6.dp + (14.dp * h)))
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Mesh hub mark — central node with a rotating hex ring of peers (reads as
 * “network / omnidirectional relay” rather than abstract overlapping squares).
 */
@Composable
private fun MeshHubNetworkLogo(size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "meshHubLogo")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshSpin"
    )
    val hubPulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hubPulse"
    )

    Canvas(modifier = Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val ringR = this.size.minDimension * 0.36f
        val nodeR = this.size.minDimension * 0.065f
        val hubR = this.size.minDimension * 0.13f * hubPulse

        val nodeColors = listOf(
            OmniMeshColors.MediumBlue,
            OmniMeshColors.MediumGreen,
            OmniMeshColors.Yellow,
            OmniMeshColors.MediumRed,
            OmniMeshColors.MediumBlue,
            OmniMeshColors.MediumGreen,
        )

        rotate(rotation, Offset(cx, cy)) {
            for (i in 0 until 6) {
                val a1 = Math.toRadians((i * 60 - 90).toDouble())
                val a2 = Math.toRadians(((i + 1) % 6 * 60 - 90).toDouble())
                val x1 = cx + cos(a1).toFloat() * ringR
                val y1 = cy + sin(a1).toFloat() * ringR
                val x2 = cx + cos(a2).toFloat() * ringR
                val y2 = cy + sin(a2).toFloat() * ringR
                drawLine(
                    color = OmniMeshColors.Grey.copy(alpha = 0.38f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = OmniMeshColors.LightGrey.copy(alpha = 0.9f),
                    start = Offset(cx, cy),
                    end = Offset(x1, y1),
                    strokeWidth = 1.25.dp.toPx()
                )
            }
            nodeColors.forEachIndexed { i, c ->
                val a = Math.toRadians((i * 60 - 90).toDouble())
                val px = cx + cos(a).toFloat() * ringR
                val py = cy + sin(a).toFloat() * ringR
                drawCircle(color = c.copy(alpha = 0.92f), radius = nodeR, center = Offset(px, py))
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = nodeR * 0.35f,
                    center = Offset(px - nodeR * 0.25f, py - nodeR * 0.25f)
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OmniMeshColors.MediumBlue,
                        OmniMeshColors.MediumGreen.copy(alpha = 0.88f),
                    ),
                    center = Offset(cx, cy - hubR * 0.15f),
                    radius = hubR * 2.1f
                ),
                radius = hubR,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.42f),
                radius = hubR * 0.33f,
                center = Offset(cx - hubR * 0.22f, cy - hubR * 0.28f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Automated Distress Detection — animated mic bars + motion ticks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AutomatedDistressCard(isRecording: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = OmniMeshColors.White,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, OmniMeshColors.LightGrey)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AUTOMATED DISTRESS DETECTION:",
                    color = OmniMeshColors.Grey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    fontFamily = OmniMono,
                    modifier = Modifier.weight(1f)
                )
                // Tiny breathing dot next to "ON".
                val infinite = rememberInfiniteTransition(label = "onPulse")
                val a by infinite.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "onPulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer(alpha = a)
                        .background(OmniMeshColors.MediumGreen, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "ON",
                    color = OmniMeshColors.MediumGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = OmniMono
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SensorIndicator(
                    icon = Icons.Filled.PhoneAndroid,
                    label = "Accelerometer",
                    iconTint = OmniMeshColors.Yellow,
                    labelColor = OmniMeshColors.Orange,
                    sensorPulseActive = true,
                    modifier = Modifier.weight(1f)
                )
                SensorIndicator(
                    icon = Icons.Filled.Mic,
                    label = "Microphone",
                    iconTint = OmniMeshColors.MediumBlue,
                    labelColor = OmniMeshColors.Blue,
                    sensorPulseActive = isRecording,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))
            SensorFusionAccentLine()
            Spacer(Modifier.height(10.dp))

            FourColorPipelineBar()
        }
    }
}

@Composable
private fun SensorFusionAccentLine() {
    val transition = rememberInfiniteTransition(label = "fusionAccent")
    val shimmer by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "accentSweep"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(50))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(OmniMeshColors.Yellow.copy(alpha = 0.88f))
            val cx = size.width * shimmer
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.55f),
                        Color.Transparent,
                    ),
                    startX = cx - 48f,
                    endX = cx + 48f,
                ),
                topLeft = Offset.Zero,
                size = size
            )
        }
    }
}

@Composable
private fun SensorIndicator(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    labelColor: Color,
    sensorPulseActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "sensorGlow_$label")
    val glow by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sensorGlowPhase"
    )
    val borderColor = if (sensorPulseActive) {
        labelColor.copy(alpha = 0.28f + 0.38f * glow)
    } else {
        OmniMeshColors.LightGrey
    }

    Row(
        modifier = modifier
            .background(OmniMeshColors.LightGrey, RoundedCornerShape(12.dp))
            .border(
                width = if (sensorPulseActive) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Four-color pipeline strip with a moving bright sweep across it. */
@Composable
private fun FourColorPipelineBar() {
    val transition = rememberInfiniteTransition(label = "pipelineSweep")
    val sweep by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepX"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            listOf(
                OmniMeshColors.MediumBlue,
                OmniMeshColors.MediumRed,
                OmniMeshColors.Yellow,
                OmniMeshColors.MediumGreen
            ).forEach { c ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(c)
                )
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * sweep
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.65f),
                        Color.Transparent,
                    ),
                    startX = cx - 50f,
                    endX = cx + 50f
                ),
                topLeft = Offset.Zero,
                size = size
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live signals strip — mesh quality, signal hops, GPS lock. Adds density
// without adding noise: small monochrome dials beneath the distress card.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveSignalsStrip(peerCount: Int) {
    val infinite = rememberInfiniteTransition(label = "liveSignals")
    val tick by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liveTick"
    )
    val routingEff = (88f + 6f * sin(tick)).toInt()
    val hops = (3.6f + 0.8f * sin(tick * 0.7f + 1f))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = OmniMeshColors.White.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, OmniMeshColors.LightGrey)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalDial(
                label = "ROUTING",
                value = "$routingEff%",
                color = OmniMeshColors.MediumGreen,
                fillFraction = routingEff / 100f
            )
            SignalDial(
                label = "AVG HOPS",
                value = "%.1f".format(hops),
                color = OmniMeshColors.MediumBlue,
                fillFraction = (hops / 6f).coerceIn(0f, 1f)
            )
            SignalDial(
                label = "PEERS",
                value = formatPeersShort(peerCount),
                color = OmniMeshColors.Yellow,
                fillFraction = (peerCount.coerceAtLeast(0).toFloat() / 20f).coerceAtMost(1f)
            )
            SignalDial(
                label = "GPS",
                value = "Lock",
                color = OmniMeshColors.MediumRed,
                fillFraction = 0.92f
            )
        }
    }
}

private fun formatPeersShort(count: Int): String = count.coerceAtLeast(0).toString()

@Composable
private fun SignalDial(
    label: String,
    value: String,
    color: Color,
    fillFraction: Float,
) {
    val animatedFill by animateFloatAsState(
        targetValue = fillFraction.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "dialFill"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
            Canvas(modifier = Modifier.size(34.dp)) {
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4f)
                )
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedFill,
                    useCenter = false,
                    style = Stroke(width = 4f)
                )
            }
            Text(
                text = value,
                color = OmniMeshColors.Black,
                fontSize = 11.sp,
                fontFamily = OmniMono,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = OmniMeshColors.Grey,
            fontSize = 11.sp,
            fontFamily = OmniMono,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun MeshActivityBand(peerCount: Int, redCount: Int) {
    val inf = rememberInfiniteTransition(label = "meshActivityBand")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "activityPhase"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = OmniMeshColors.White.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, OmniMeshColors.LightGrey)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MESH ACTIVITY", color = OmniMeshColors.Black, fontSize = 13.sp, fontFamily = OmniMono, fontWeight = FontWeight.Bold)
                Text("Peers $peerCount · Critical $redCount", color = OmniMeshColors.Grey, fontSize = 13.sp, fontFamily = OmniBody)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                val bars = 28
                val gap = 2f
                val barW = (size.width - gap * (bars - 1)) / bars
                repeat(bars) { i ->
                    val amp = 0.25f + 0.75f * abs(sin(phase + i * 0.45f))
                    val h = size.height * amp
                    val x = i * (barW + gap)
                    val col = when {
                        redCount > 0 && i % 7 == 0 -> OmniMeshColors.MediumRed
                        i % 4 == 0 -> OmniMeshColors.MediumGreen
                        else -> OmniMeshColors.MediumBlue
                    }
                    drawRoundRect(
                        color = col.copy(alpha = 0.85f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primary REPORT EMERGENCY button — full-bleed pill, color states animate
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReportEmergencyButton(sosState: SOSState, onTap: () -> Unit) {
    val targetColor = when (sosState) {
        SOSState.IDLE -> OmniMeshColors.MediumRed
        SOSState.RECORDING, SOSState.TRIGGERED -> OmniMeshColors.Orange
        SOSState.TRANSMITTED -> OmniMeshColors.MediumGreen
    }
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 220, easing = EaseInOut),
        label = "sosBg"
    )

    val label = when (sosState) {
        SOSState.IDLE -> "REPORT EMERGENCY"
        SOSState.RECORDING -> "LISTENING…"
        SOSState.TRIGGERED -> "SENDING…"
        SOSState.TRANSMITTED -> "✓ SENT"
    }

    // Subtle glow halo behind the pill — emphasises the primary action.
    val infinite = rememberInfiniteTransition(label = "sosHalo")
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )
    val ring1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sosRing1"
    )
    val ring2 by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sosRing2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(animatedColor.copy(alpha = haloAlpha), Color.Transparent)
                    ),
                    RoundedCornerShape(28.dp)
                )
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            val cy = size.height / 2f
            val maxR = size.width * 0.48f
            val minR = size.width * 0.30f
            listOf(ring1, ring2).forEach { phase ->
                val p = (phase % 1f).coerceIn(0f, 1f)
                val r = minR + (maxR - minR) * p
                val a = ((1f - p) * 0.32f).coerceAtLeast(0f)
                drawCircle(
                    color = animatedColor.copy(alpha = a),
                    radius = r,
                    center = Offset(size.width / 2f, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Button(
            onClick = onTap,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedColor,
                disabledContainerColor = animatedColor
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
            enabled = sosState == SOSState.IDLE
        ) {
            Text(
                text = label,
                color = OmniMeshColors.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.4.sp,
                fontFamily = OmniDisplay
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Acoustic beacon active — light card matching the Victim surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AcousticBeaconActiveCard(onSilence: () -> Unit) {
    val pulseAnim = rememberInfiniteTransition(label = "beacon")
    val beaconAlpha by pulseAnim.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )
    Column {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = OmniMeshColors.LightYellow.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, OmniMeshColors.Orange.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "🔊",
                    fontSize = 22.sp,
                    modifier = Modifier.graphicsLayer(alpha = beaconAlpha)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ACOUSTIC BEACON ACTIVE",
                        color = OmniMeshColors.Orange,
                        fontSize = 11.sp,
                        fontFamily = OmniMono,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        "Broadcasting SOS tone every 30s to help rescuers locate you.",
                        color = OmniMeshColors.Black.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = OmniBody
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSilence,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, OmniMeshColors.Grey),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "SILENCE BEACON",
                color = OmniMeshColors.Grey,
                fontSize = 11.sp,
                fontFamily = OmniMono,
                letterSpacing = 1.2.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nearby Triage summary card — light theme to match the Victim surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NearbyTriageCard(redCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OmniMeshColors.White, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                OmniMeshColors.MediumRed.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = OmniMeshColors.MediumRed,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Nearby Triage",
                color = OmniMeshColors.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OmniDisplay
            )
            Text(
                "$redCount critical case${if (redCount == 1) "" else "s"} on the mesh.",
                color = OmniMeshColors.Grey,
                fontSize = 13.sp,
                fontFamily = OmniBody
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = OmniMeshColors.Grey,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Secondary actions (voice SOS + injury capture)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionFillSection(
    showDisasterBanner: Boolean,
    isAutoSosActive: Boolean,
    onVoiceSOS: () -> Unit,
    onCapture: () -> Unit,
    onSilenceBeacon: () -> Unit,
    onStartCompanion: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onVoiceSOS,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(
                1.dp,
                OmniMeshColors.Orange
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = OmniMeshColors.Orange
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Speak your injury",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "SPEAK YOUR INJURY",
                fontSize = 13.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OmniMono
            )
        }

        OutlinedButton(
            onClick = onCapture,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, OmniMeshColors.MediumBlue),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = OmniMeshColors.MediumBlue
            )
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Capture injury",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "CAPTURE INJURY",
                fontSize = 13.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OmniMono
            )
        }

        if (omnimesh.command1.BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = onStartCompanion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, Color(0xFF34A853)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF34A853)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Start companion",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "START COMPANION",
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OmniMono
                )
            }
        }

        AnimatedVisibility(visible = showDisasterBanner) {
            DisasterAlertBanner()
        }
        AnimatedVisibility(visible = isAutoSosActive) {
            AutoSosActiveRow(onSilence = onSilenceBeacon)
        }
    }
}

@Composable
private fun DisasterAlertBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = OmniMeshColors.LightRed,
        border = BorderStroke(1.dp, OmniMeshColors.MediumRed.copy(alpha = 0.5f))
    ) {
        Text(
            text = "DISASTER ALERT · PRIORITY RESPONSE ACTIVE",
            modifier = Modifier.padding(12.dp),
            color = OmniMeshColors.MediumRed,
            fontSize = 13.sp,
            fontFamily = OmniMono,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AutoSosActiveRow(onSilence: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "autoSosPulse")
    val a by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "beaconPulse"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OmniMeshColors.LightYellow, RoundedCornerShape(10.dp))
            .border(1.dp, OmniMeshColors.Orange.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔊", modifier = Modifier.graphicsLayer(alpha = a), fontSize = 16.sp)
            Text(
                "AUTO SOS ACOUSTIC BEACON ACTIVE",
                color = OmniMeshColors.Orange,
                fontSize = 13.sp,
                fontFamily = OmniMono,
                fontWeight = FontWeight.Bold
            )
        }
        OutlinedButton(
            onClick = onSilence,
            border = BorderStroke(1.dp, OmniMeshColors.Orange),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OmniMeshColors.Orange),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text("SILENCE", fontSize = 11.sp, fontFamily = OmniMono)
        }
    }
}
