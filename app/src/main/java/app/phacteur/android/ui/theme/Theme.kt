package app.phacteur.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF101B61),
    secondary = Color(0xFF585D72),
    secondaryContainer = Color(0xFFDDE1F9),
    tertiary = Color(0xFF75546F),
    tertiaryContainer = Color(0xFFFFD7F7),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    surfaceVariant = Color(0xFFE3E2EC),
    outline = Color(0xFF777680),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF10236A),
    primaryContainer = Color(0xFF293A83),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFFC1C5DD),
    secondaryContainer = Color(0xFF414659),
    tertiary = Color(0xFFE4BADB),
    tertiaryContainer = Color(0xFF5B3C57),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF91909A),
)

@Composable
fun PhacteurTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors

    (context as? Activity)?.window?.let { window ->
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
    }

    MaterialTheme(colorScheme = colors, content = content)
}
