package omnimesh.command1.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import omnimesh.command1.ui.theme.OmniMeshColors

@Composable
fun OmniMeshTopBar(
    screenName: String,
    peerCount: Int,
    showTimezone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val clock = remember { mutableStateOf("") }
    val timezone = remember {
        val tz = TimeZone.getDefault()
        val offset = tz.rawOffset / 3600000
        val id = tz.getDisplayName(false, TimeZone.SHORT)
        "$id ${if (offset >= 0) "+" else ""}$offset:00"
    }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            clock.value = fmt.format(Date())
            delay(1000)
        }
    }

    val brandTransition = rememberInfiniteTransition(label = "omnimeshWordmark")
    val gradientTravel by brandTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wordmarkGradientTravel",
    )

    val omnimeshStyle = remember(peerCount, gradientTravel) {
        val letter = 2.sp
        val size = 14.sp
        val mono = FontFamily.Monospace
        val weight = FontWeight.Bold
        if (peerCount <= 0) {
            TextStyle(
                color = OmniMeshColors.MediumBlue,
                fontSize = size,
                fontFamily = mono,
                fontWeight = weight,
                letterSpacing = letter,
            )
        } else {
            val slide = gradientTravel * 260f
            TextStyle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        OmniMeshColors.Blue,
                        OmniMeshColors.MediumBlue,
                        OmniMeshColors.MediumGreen,
                        OmniMeshColors.LightBlue,
                        OmniMeshColors.MediumBlue,
                        OmniMeshColors.Blue,
                    ),
                    start = Offset(-80f + slide, 0f),
                    end = Offset(120f + slide, 28f),
                ),
                fontSize = size,
                fontFamily = mono,
                fontWeight = weight,
                letterSpacing = letter,
            )
        }
    }

    Row(
        modifier = modifier
            .background(Color(0xFF0D1117).copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "OMNIMESH", style = omnimeshStyle)
        Text("|", color = Color(0xFF2C2C2E), fontSize = 11.sp)
        Text(
            screenName,
            color = Color(0xFF9AA0A6),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.weight(1f))

        val pillColor = if (peerCount > 0) Color(0xFF34A853) else Color(0xFF5F6368)
        Row(
            modifier = Modifier
                .background(pillColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .border(1.dp, pillColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).background(pillColor, CircleShape))
            Text(
                "$peerCount PEERS",
                color = pillColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            clock.value,
            color = Color(0xFFFFFFFF),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
        if (showTimezone) {
            Text(
                timezone,
                color = Color(0xFF9AA0A6),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
