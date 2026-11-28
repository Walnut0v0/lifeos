package io.lifeos.focuslab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val FocusInk: Color @Composable get() = MaterialTheme.colorScheme.primary
val FocusMint: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val FocusCard: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest

@Composable fun LifeOsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFB2D0BE), onPrimary = Color(0xFF203B30),
        primaryContainer = Color(0xFF30463A), onPrimaryContainer = Color(0xFFDCECDF),
        background = Color(0xFF17221D), surface = Color(0xFF17221D), surfaceContainerLowest = Color(0xFF223129),
        onSurface = Color(0xFFE9ECE8), onBackground = Color(0xFFE9ECE8), onSurfaceVariant = Color(0xFFBAC3BC),
        outline = Color(0xFF8D9B90), secondaryContainer = Color(0xFF30463A), onSecondaryContainer = Color(0xFFDCECDF)
    ) else lightColorScheme(
        primary = Color(0xFF315C4B), onPrimary = Color.White,
        primaryContainer = Color(0xFFE1ECE3), onPrimaryContainer = Color(0xFF203B30),
        background = Color(0xFFF5F7F3), surface = Color(0xFFF5F7F3), surfaceContainerLowest = Color.White,
        onSurface = Color(0xFF202722), onBackground = Color(0xFF202722), onSurfaceVariant = Color(0xFF56635B),
        outline = Color(0xFF789487), secondaryContainer = Color(0xFFE1ECE3), onSecondaryContainer = Color(0xFF203B30)
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
    ), content = content)
}
