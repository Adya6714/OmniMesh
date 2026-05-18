package omnimesh.command1.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnimesh.command1.companion.CompanionMessage
import omnimesh.command1.companion.CompanionState
import omnimesh.command1.companion.MessageRole
import omnimesh.command1.companion.VictimClinicalState

@Composable
fun CompanionScreen(
    messages: List<CompanionMessage>,
    state: CompanionState,
    clinicalState: VictimClinicalState,
    isListening: Boolean,
    onSendText: (String) -> Unit,
    onStartVoice: () -> Unit,
    onEndSession: () -> Unit,
) {
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .imePadding()
    ) {
        CompanionHeader(state = state, clinicalState = clinicalState, onEndSession = onEndSession)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 2 }) {
                    CompanionMessageBubble(message = message)
                }
            }
        }
        AnimatedVisibility(visible = clinicalState.reportedInjuries.isNotEmpty()) {
            ClinicalStateBanner(clinicalState = clinicalState)
        }
        CompanionInputBar(
            textInput = textInput,
            onTextChange = { textInput = it },
            onSendText = {
                if (textInput.isNotBlank()) {
                    onSendText(textInput)
                    textInput = ""
                    focusManager.clearFocus()
                }
            },
            onStartVoice = onStartVoice,
            isListening = isListening,
            state = state,
        )
    }
}

@Composable
private fun CompanionHeader(
    state: CompanionState,
    clinicalState: VictimClinicalState,
    onEndSession: () -> Unit,
) {
    val pulseAnim = rememberInfiniteTransition(label = "companion_pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1117))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF4285F4).copy(alpha = pulseAlpha * 0.2f), CircleShape)
                    .border(2.dp, Color(0xFF4285F4).copy(alpha = pulseAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🛡", fontSize = 20.sp) }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "OMNIMESH COMPANION",
                    color = Color(0xFF4285F4),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    when (state) {
                        CompanionState.ACTIVATING -> "Connecting..."
                        CompanionState.ASSESSING -> "Assessing your condition"
                        CompanionState.SUPPORTING -> "With you until rescue arrives"
                        CompanionState.BRIDGING -> "Connecting to rescue team"
                        CompanionState.ENDED -> "Session ended"
                        CompanionState.IDLE -> "Ready"
                    },
                    color = Color(0xFF9AA0A6),
                    fontSize = 12.sp
                )
            }

            val urgency = clinicalState.computeUrgency()
            val urgencyColor = when (urgency) {
                "RED" -> Color(0xFFEA4335)
                "YELLOW" -> Color(0xFFFBBC04)
                "GREEN" -> Color(0xFF34A853)
                else -> Color(0xFF9AA0A6)
            }
            Box(
                modifier = Modifier
                    .background(urgencyColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, urgencyColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    urgency,
                    color = urgencyColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onEndSession) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit companion",
                    tint = Color(0xFF9AA0A6)
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (state == CompanionState.SUPPORTING) 1f else 0.5f },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color(0xFF4285F4),
            trackColor = Color.Transparent
        )
    }
}

@Composable
private fun CompanionMessageBubble(message: CompanionMessage) {
    val isCompanion = message.role == MessageRole.COMPANION
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCompanion) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = when (message.role) {
                        MessageRole.COMPANION -> Color(0xFF174EA6).copy(alpha = 0.2f)
                        MessageRole.VICTIM -> Color(0xFF1C2025)
                        MessageRole.SYSTEM -> Color(0xFF2C2C2E)
                    },
                    shape = RoundedCornerShape(
                        topStart = if (isCompanion) 4.dp else 12.dp,
                        topEnd = if (isCompanion) 12.dp else 4.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                )
                .border(
                    1.dp,
                    if (isCompanion) Color(0xFF4285F4).copy(alpha = 0.3f) else Color(0xFF2C2C2E),
                    RoundedCornerShape(
                        topStart = if (isCompanion) 4.dp else 12.dp,
                        topEnd = if (isCompanion) 12.dp else 4.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                if (isCompanion) {
                    Text(
                        "COMPANION",
                        color = Color(0xFF4285F4),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(text = message.text, color = Color(0xFFE8EAED), fontSize = 15.sp, lineHeight = 22.sp)
                if (message.updatesTriagePacket) {
                    Spacer(Modifier.height(4.dp))
                    Text("⚡ Urgency updated", color = Color(0xFFEA4335), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun ClinicalStateBanner(clinicalState: VictimClinicalState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C2025))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.MedicalServices,
            contentDescription = null,
            tint = Color(0xFF4285F4),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = clinicalState.reportedInjuries.take(3).joinToString(" · "),
            color = Color(0xFF9AA0A6),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        clinicalState.isTrapped?.let {
            if (it) Text("TRAPPED", color = Color(0xFFEA4335), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompanionInputBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onStartVoice: () -> Unit,
    isListening: Boolean,
    state: CompanionState,
) {
    val pulseAnim = rememberInfiniteTransition(label = "listen_pulse")
    val listenScale by pulseAnim.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C2025))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = textInput,
            onValueChange = onTextChange,
            placeholder = { Text("Type if you can't speak...", color = Color(0xFF5F6368), fontSize = 13.sp) },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4285F4),
                unfocusedBorderColor = Color(0xFF2C2C2E),
                focusedTextColor = Color(0xFFE8EAED),
                unfocusedTextColor = Color(0xFFE8EAED),
                cursorColor = Color(0xFF4285F4),
            ),
            shape = RoundedCornerShape(8.dp),
            maxLines = 3,
        )

        if (textInput.isNotBlank()) {
            IconButton(
                onClick = onSendText,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF174EA6), CircleShape)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        IconButton(
            onClick = onStartVoice,
            enabled = !isListening && state != CompanionState.ENDED,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer(
                    scaleX = if (isListening) listenScale else 1f,
                    scaleY = if (isListening) listenScale else 1f,
                )
                .background(if (isListening) Color(0xFFEA4335) else Color(0xFF4285F4), CircleShape)
        ) {
            Icon(
                if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isListening) "Listening..." else "Speak",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
