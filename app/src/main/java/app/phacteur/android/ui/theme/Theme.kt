package app.phacteur.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Keep these HSL values aligned with the Phacteur web workspace's globals.css.
private fun hsl(hue: Int, saturation: Int, lightness: Int) =
    Color.hsl(hue.toFloat(), saturation / 100f, lightness / 100f)

private val LightColors = lightColorScheme(
    primary = hsl(239, 72, 59),
    onPrimary = Color.White,
    primaryContainer = hsl(235, 75, 96),
    onPrimaryContainer = hsl(239, 65, 46),
    inversePrimary = hsl(237, 87, 74),
    secondary = hsl(171, 57, 30),
    onSecondary = Color.White,
    secondaryContainer = hsl(225, 22, 95),
    onSecondaryContainer = hsl(224, 20, 25),
    tertiary = hsl(28, 70, 35),
    onTertiary = Color.White,
    tertiaryContainer = hsl(28, 82, 95),
    onTertiaryContainer = hsl(28, 70, 35),
    background = hsl(220, 20, 98),
    onBackground = hsl(224, 25, 15),
    surface = Color.White,
    onSurface = hsl(224, 25, 15),
    surfaceVariant = hsl(220, 18, 96),
    onSurfaceVariant = hsl(222, 10, 44),
    surfaceTint = hsl(239, 72, 59),
    inverseSurface = hsl(225, 18, 11),
    inverseOnSurface = hsl(220, 20, 92),
    surfaceBright = Color.White,
    surfaceDim = hsl(222, 18, 90),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = hsl(220, 20, 98),
    surfaceContainer = hsl(220, 18, 96),
    surfaceContainerHigh = hsl(225, 22, 95),
    surfaceContainerHighest = hsl(222, 18, 90),
    outline = hsl(222, 18, 85),
    outlineVariant = hsl(222, 18, 90),
    error = hsl(0, 65, 48),
    onError = Color.White,
    errorContainer = hsl(0, 65, 96),
    onErrorContainer = hsl(0, 65, 36),
)

private val DarkColors = darkColorScheme(
    primary = hsl(237, 87, 74),
    onPrimary = hsl(230, 30, 12),
    primaryContainer = hsl(235, 30, 21),
    onPrimaryContainer = hsl(237, 87, 80),
    inversePrimary = hsl(239, 72, 59),
    secondary = hsl(171, 50, 64),
    onSecondary = hsl(230, 30, 12),
    secondaryContainer = hsl(224, 17, 17),
    onSecondaryContainer = hsl(220, 20, 88),
    tertiary = hsl(28, 82, 74),
    onTertiary = hsl(230, 30, 12),
    tertiaryContainer = hsl(28, 30, 21),
    onTertiaryContainer = hsl(28, 82, 74),
    background = hsl(225, 20, 8),
    onBackground = hsl(220, 20, 92),
    surface = hsl(225, 18, 11),
    onSurface = hsl(220, 20, 92),
    surfaceVariant = hsl(224, 17, 15),
    onSurfaceVariant = hsl(220, 12, 65),
    surfaceTint = hsl(237, 87, 74),
    inverseSurface = hsl(220, 20, 92),
    inverseOnSurface = hsl(224, 25, 15),
    surfaceBright = hsl(224, 16, 22),
    surfaceDim = hsl(225, 20, 8),
    surfaceContainerLowest = hsl(225, 20, 8),
    surfaceContainerLow = hsl(225, 18, 11),
    surfaceContainer = hsl(225, 18, 13),
    surfaceContainerHigh = hsl(224, 17, 15),
    surfaceContainerHighest = hsl(224, 17, 17),
    outline = hsl(224, 16, 29),
    outlineVariant = hsl(224, 16, 22),
    error = hsl(0, 72, 65),
    onError = Color.White,
    errorContainer = hsl(0, 35, 20),
    onErrorContainer = hsl(0, 72, 85),
)

private fun textStyle(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = tracking.sp,
    lineHeight = height.sp,
)

private val PhacteurTypography = Typography(
    headlineLarge = textStyle(30, 36, FontWeight.SemiBold, -0.75f),
    headlineMedium = textStyle(26, 32, FontWeight.SemiBold, -0.65f),
    headlineSmall = textStyle(24, 30, FontWeight.SemiBold, -0.6f),
    titleLarge = textStyle(22, 28, FontWeight.SemiBold, -0.55f),
    titleMedium = textStyle(16, 24, FontWeight.SemiBold, -0.2f),
    titleSmall = textStyle(14, 20, FontWeight.Medium),
    bodyLarge = textStyle(16, 24),
    bodyMedium = textStyle(14, 20),
    bodySmall = textStyle(12, 18),
    labelLarge = textStyle(14, 20, FontWeight.SemiBold),
    labelMedium = textStyle(12, 16, FontWeight.Medium),
    labelSmall = textStyle(11, 16, FontWeight.Medium, 0.1f),
)

private val PhacteurShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun mailboxIdentityColor(index: Int): Color {
    val dark = isSystemInDarkTheme()
    return when (Math.floorMod(index, 3)) {
        1 -> if (dark) hsl(171, 50, 52) else hsl(171, 57, 39)
        2 -> if (dark) hsl(28, 82, 65) else hsl(28, 82, 57)
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
fun mailboxGroupColor(color: String?): Color =
    color?.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }
        ?.let { Color(android.graphics.Color.parseColor(it)) }
        ?: MaterialTheme.colorScheme.primary

@Composable
fun PhacteurTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = PhacteurTypography,
        shapes = PhacteurShapes,
        content = content,
    )
}
