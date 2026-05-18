package omnimesh.command1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnimesh.command1.command.DataQualityCalculator
import omnimesh.command1.command.QualityLevel
import omnimesh.command1.data.TriagePacket

@Composable
fun DataQualityBadge(
    packet: TriagePacket,
    modifier: Modifier = Modifier,
) {
    val quality = DataQualityCalculator.compute(packet)
    val color = when (quality.level) {
        QualityLevel.HIGH -> Color(0xFF34A853)
        QualityLevel.MEDIUM -> Color(0xFFFBBC04)
        QualityLevel.LOW -> Color(0xFF9AA0A6)
        QualityLevel.CONFIRMED -> Color(0xFF34A853)
        QualityLevel.DISPUTED -> Color(0xFFEA4335)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when (quality.level) {
            QualityLevel.CONFIRMED -> {
                Icon(Icons.Filled.Check, contentDescription = "Confirmed", tint = color)
                Text("CONFIRMED", color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            QualityLevel.DISPUTED -> {
                Icon(Icons.Filled.Close, contentDescription = "False positive", tint = color)
                Text("FALSE+", color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            else -> {
                repeat(3) { barIndex ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(
                                when (barIndex) {
                                    0 -> 6.dp
                                    1 -> 9.dp
                                    else -> 12.dp
                                }
                            )
                            .background(
                                if (barIndex < quality.barsFilled) color else color.copy(alpha = 0.2f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun DataQualityDetail(
    packet: TriagePacket,
    onConfirm: () -> Unit,
    onFalsePositive: () -> Unit,
    onReached: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val quality = DataQualityCalculator.compute(packet)
    val color = when (quality.level) {
        QualityLevel.HIGH -> Color(0xFF34A853)
        QualityLevel.MEDIUM -> Color(0xFFFBBC04)
        QualityLevel.LOW -> Color(0xFF9AA0A6)
        QualityLevel.CONFIRMED -> Color(0xFF34A853)
        QualityLevel.DISPUTED -> Color(0xFFEA4335)
    }

    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DataQualityBadge(packet = packet)
            Column {
                Text(quality.label, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(quality.detail, color = Color(0xFF9AA0A6), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        if (quality.level != QualityLevel.CONFIRMED && quality.level != QualityLevel.DISPUTED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF4285F4).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF4285F4).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onReached)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("📍 REACHED", color = Color(0xFF4285F4), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF34A853).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF34A853).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("✓ CONFIRM", color = Color(0xFF34A853), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEA4335).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFEA4335).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onFalsePositive)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("✗ FALSE POSITIVE", color = Color(0xFFEA4335), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
