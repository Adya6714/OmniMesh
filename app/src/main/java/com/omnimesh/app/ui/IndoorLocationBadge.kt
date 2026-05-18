package omnimesh.command1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
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
import omnimesh.command1.location.IndoorLocation
import omnimesh.command1.location.LocationMethod

/**
 * Compact badge showing current location quality and floor estimate.
 * Shown on VictimScreen and in packet detail views.
 */
@Composable
fun IndoorLocationBadge(
    location: IndoorLocation?,
    modifier: Modifier = Modifier
) {
    if (location == null) return

    val methodColor = when (location.method) {
        LocationMethod.GPS, LocationMethod.GPS_PLUS_FLOOR -> Color(0xFF34A853)
        LocationMethod.RSSI_TRIANGULATION -> Color(0xFF4285F4)
        LocationMethod.RSSI_SINGLE_PEER -> Color(0xFFE37400)
        LocationMethod.BAROMETRIC_FLOOR -> Color(0xFFFBBC04)
        LocationMethod.LAST_KNOWN -> Color(0xFF9AA0A6)
    }

    val methodLabel = when (location.method) {
        LocationMethod.GPS -> "GPS"
        LocationMethod.GPS_PLUS_FLOOR -> "GPS+FLOOR"
        LocationMethod.RSSI_TRIANGULATION -> "MESH FIX (${location.contributingPeerCount} peers)"
        LocationMethod.RSSI_SINGLE_PEER -> "MESH EST"
        LocationMethod.BAROMETRIC_FLOOR -> "PRESSURE"
        LocationMethod.LAST_KNOWN -> "LAST KNOWN"
    }

    Row(
        modifier = modifier
            .background(
                Color(0xFF1C2025),
                RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                methodColor.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = methodColor,
            modifier = Modifier.size(14.dp)
        )
        Column {
            Text(
                text = methodLabel,
                color = methodColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "±${location.accuracyMeters.toInt()}m",
                color = Color(0xFF9AA0A6),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        location.estimatedFloor?.let { floor ->
            if (location.floorConfidence > 0.4f) {
                Spacer(Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = Color(0xFFFBBC04),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = when {
                            floor == 0 -> "GF"
                            floor < 0 -> "B${-floor}"
                            else -> "F$floor"
                        },
                        color = Color(0xFFFBBC04),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
