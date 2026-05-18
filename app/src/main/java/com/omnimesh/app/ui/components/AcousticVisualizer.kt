package omnimesh.command1.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import omnimesh.command1.ui.theme.OmniMeshColors

private val defaultBarDurationsMs = listOf(400, 600, 300, 500)

/**
 * Compact animated level meters — reads as “actively sampling” without Canvas work.
 */
@Composable
fun AcousticVisualizer(
    modifier: Modifier = Modifier,
    barColor: Color = OmniMeshColors.MediumRed,
    barWidth: Dp = 4.dp,
    rowHeight: Dp = 24.dp,
) {
    Row(
        modifier = modifier.height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        defaultBarDurationsMs.forEach { durationMs ->
            key(durationMs) {
                AcousticBar(durationMs = durationMs, barColor = barColor, barWidth = barWidth)
            }
        }
    }
}

@Composable
private fun AcousticBar(
    durationMs: Int,
    barColor: Color,
    barWidth: Dp,
) {
    val transition = rememberInfiniteTransition(label = "acousticBar_$durationMs")
    val heightMultiplier by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "height_$durationMs",
    )
    Box(
        modifier = Modifier
            .width(barWidth)
            .fillMaxHeight(heightMultiplier)
            .clip(RoundedCornerShape(2.dp))
            .background(barColor),
    )
}
