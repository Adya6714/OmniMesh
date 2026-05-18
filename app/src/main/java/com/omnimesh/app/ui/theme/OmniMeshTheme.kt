package omnimesh.command1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import omnimesh.command1.R

// Exact Google marketing palette. These values are the source of truth for
// every screen. Any drift breaks the design system.
object OmniMeshColors {
    val Blue = Color(0xFF174EA6)
    val MediumBlue = Color(0xFF4285F4)
    val LightBlue = Color(0xFFD2E3FC)
    val Red = Color(0xFFA50E0E)
    val MediumRed = Color(0xFFEA4335)
    val LightRed = Color(0xFFFAD2CF)
    val Orange = Color(0xFFE37400)
    val Yellow = Color(0xFFFBBC04)
    val LightYellow = Color(0xFFFEEFC3)
    val Green = Color(0xFF0D652D)
    val MediumGreen = Color(0xFF34A853)
    val LightGreen = Color(0xFFCEEAD6)
    val LightGrey = Color(0xFFF1F3F4)
    val Grey = Color(0xFF9AA0A6)
    val Black = Color(0xFF202124)
    val White = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFAFAFA)
    val DarkSurface = Color(0xFF1C2025)
    val DarkBackground = Color(0xFF0D1117)

    // Semantic aliases used by tactical surfaces (same hex values as before).
    val DarkElevated = Color(0xFF161B22)
    val DarkInk = Color(0xFF0A0A0A)
    val GreyDeceased = Color(0xFF424242)
    val RedCritical = MediumRed
    val RedCriticalDim = Red
    val AmberSerious = Yellow
    val GreenMinor = MediumGreen
    val CommandBlue = MediumBlue
    val BackgroundDark = DarkBackground
    val SurfaceDark = DarkSurface
    val OnSurface = Color(0xFFEEEEEE)
    val PulseRed = Color(0xFFFF5252)
}

// Downloadable Google Fonts provider. Resolves at runtime against Google
// Play Services so we don't ship the font binaries. The certificate
// arrays in res/values/font_certs.xml gate which signed packages can serve
// the fonts.
private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private fun gFont(name: String, weight: FontWeight) = Font(
    googleFont = GoogleFont(name),
    fontProvider = GoogleFontProvider,
    weight = weight
)

// Display family — Outfit. A geometric sans inspired by the Google
// marketing wordmark, used for large numerals, headlines and the
// "OMNIMESH ACTIVE" title.
val OmniDisplay: FontFamily = FontFamily(
    gFont("Outfit", FontWeight.Light),
    gFont("Outfit", FontWeight.Normal),
    gFont("Outfit", FontWeight.Medium),
    gFont("Outfit", FontWeight.SemiBold),
    gFont("Outfit", FontWeight.Bold),
    gFont("Outfit", FontWeight.Black),
)

// Body family — Inter. Pairs cleanly with Outfit and renders crisp at
// the small sizes used across the tactical UI surfaces.
val OmniBody: FontFamily = FontFamily(
    gFont("Inter", FontWeight.Light),
    gFont("Inter", FontWeight.Normal),
    gFont("Inter", FontWeight.Medium),
    gFont("Inter", FontWeight.SemiBold),
    gFont("Inter", FontWeight.Bold),
)

// Mono family — JetBrains Mono. Used for the tactical ALL CAPS labels
// (CRIT, AUTO, %, SECTOR codes) so they read as data rather than copy.
val OmniMono: FontFamily = FontFamily(
    gFont("JetBrains Mono", FontWeight.Normal),
    gFont("JetBrains Mono", FontWeight.Medium),
    gFont("JetBrains Mono", FontWeight.Bold),
)

// Backwards-compat aliases — every existing call site keeps working but
// now resolves to a real downloadable font instead of system sans-serif.
val GoogleSans: FontFamily = OmniDisplay

object OmniMeshType {
    val labelSmall = 10.sp
    val labelMedium = 12.sp
    val labelLarge = 13.sp

    val bodySmall = 13.sp
    val bodyMedium = 14.sp
    val bodyLarge = 15.sp

    val dataSmall = 12.sp
    val dataMedium = 13.sp

    val displaySmall = 28.sp
    val displayMedium = 36.sp
    val displayLarge = 48.sp
}

private val OmniTypography = Typography(
    displayLarge = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Light, fontSize = 48.sp),
    displayMedium = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Light, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Medium, fontSize = 24.sp),
    headlineMedium = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = OmniBody, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = OmniBody, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = OmniBody, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = OmniDisplay, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
)

private val LightColorScheme = lightColorScheme(
    primary = OmniMeshColors.MediumBlue,
    onPrimary = OmniMeshColors.White,
    secondary = OmniMeshColors.MediumGreen,
    onSecondary = OmniMeshColors.White,
    tertiary = OmniMeshColors.MediumRed,
    background = OmniMeshColors.LightGrey,
    onBackground = OmniMeshColors.Black,
    surface = OmniMeshColors.White,
    onSurface = OmniMeshColors.Black,
    surfaceVariant = OmniMeshColors.Surface,
    error = OmniMeshColors.MediumRed,
    onError = OmniMeshColors.White
)

private val DarkColorScheme = darkColorScheme(
    primary = OmniMeshColors.MediumBlue,
    onPrimary = OmniMeshColors.White,
    secondary = OmniMeshColors.Yellow,
    onSecondary = OmniMeshColors.Black,
    tertiary = OmniMeshColors.MediumRed,
    background = OmniMeshColors.DarkBackground,
    onBackground = OmniMeshColors.White,
    surface = OmniMeshColors.DarkSurface,
    onSurface = OmniMeshColors.White,
    surfaceVariant = OmniMeshColors.DarkElevated,
    error = OmniMeshColors.MediumRed,
    onError = OmniMeshColors.White
)

@Composable
fun OmniMeshTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = OmniTypography,
        content = content
    )
}

fun urgencyColor(urgency: String): Color = when (urgency) {
    "RED" -> OmniMeshColors.MediumRed
    "YELLOW" -> OmniMeshColors.Yellow
    "GREEN" -> OmniMeshColors.MediumGreen
    "BLACK" -> OmniMeshColors.GreyDeceased
    else -> OmniMeshColors.Grey
}
