package omnimesh.command1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnimesh.command1.command.TimelineEvent
import omnimesh.command1.command.TimelineEventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IncidentTimelineView(
    events: List<TimelineEvent>,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) {
        Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No events recorded yet", color = Color(0xFF5F6368), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        return
    }
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items(events, key = { it.id }) { event ->
            TimelineEventRow(event = event, timeFormat = timeFormat)
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: TimelineEvent,
    timeFormat: SimpleDateFormat,
) {
    val (dotColor, icon) = when (event.type) {
        TimelineEventType.AUTO_SOS_TRIGGERED,
        TimelineEventType.RED_PACKET_DETECTED -> Color(0xFFEA4335) to "🆘"
        TimelineEventType.STRUCTURAL_WARNING -> Color(0xFFFBBC04) to "⚠"
        TimelineEventType.GEMINI_ANALYSIS_UPDATED -> Color(0xFF34A853) to "⚡"
        TimelineEventType.SECTOR_CLAIMED -> Color(0xFF4285F4) to "📍"
        TimelineEventType.SECTOR_CLEARED -> Color(0xFF34A853) to "✓"
        TimelineEventType.VICTIM_CONFIRMED -> Color(0xFF34A853) to "✓"
        TimelineEventType.FALSE_POSITIVE_REPORTED -> Color(0xFF9AA0A6) to "✗"
        TimelineEventType.BUDDY_ALERT_SENT -> Color(0xFFFBBC04) to "👥"
        TimelineEventType.INCIDENT_DECLARED -> Color(0xFFEA4335) to "🚨"
        TimelineEventType.INCIDENT_CLOSED -> Color(0xFF9AA0A6) to "🔒"
        TimelineEventType.RESPONDER_ENTERED_FIELD -> Color(0xFF4285F4) to "🎽"
        else -> Color(0xFF9AA0A6) to "•"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(20.dp)
                    .background(dotColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, dotColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 10.sp)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(Color(0xFF2C2C2E))
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    timeFormat.format(Date(event.timestamp)),
                    color = Color(0xFF5F6368),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                event.urgency?.let { urgency ->
                    val urgencyColor = when (urgency) {
                        "RED" -> Color(0xFFEA4335)
                        "YELLOW" -> Color(0xFFFBBC04)
                        "GREEN" -> Color(0xFF34A853)
                        else -> Color(0xFF9AA0A6)
                    }
                    Box(
                        modifier = Modifier
                            .background(urgencyColor.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            urgency,
                            color = urgencyColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(event.title, color = Color(0xFFE8EAED), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(event.detail, color = Color(0xFF9AA0A6), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
