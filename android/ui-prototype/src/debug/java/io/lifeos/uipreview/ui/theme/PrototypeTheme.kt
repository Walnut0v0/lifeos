package io.lifeos.uipreview.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.lifeos.uipreview.ui.screens.DesignStyle

@Composable fun PrototypeTheme(style: DesignStyle, dark: Boolean, content: @Composable () -> Unit) {
    val primary = when (style) {
        DesignStyle.NATURE -> if (dark) Color(0xFFB2D0BE) else Color(0xFF315C4B)
        DesignStyle.MONO -> if (dark) Color(0xFFF0F0F0) else Color(0xFF181818)
        DesignStyle.MATERIAL -> if (dark) Color(0xFFCCBEFF) else Color(0xFF6650A4)
    }
    val background = when (style) {
        DesignStyle.NATURE -> if (dark) Color(0xFF17221D) else Color(0xFFF5F7F3)
        DesignStyle.MONO -> if (dark) Color(0xFF111111) else Color(0xFFFAFAFA)
        DesignStyle.MATERIAL -> if (dark) Color(0xFF1C1824) else Color(0xFFF7F2FA)
    }
    val container = when (style) {
        DesignStyle.NATURE -> if (dark) Color(0xFF30463A) else Color(0xFFE1ECE3)
        DesignStyle.MONO -> if (dark) Color(0xFF303030) else Color(0xFFECECEC)
        DesignStyle.MATERIAL -> if (dark) Color(0xFF493A68) else Color(0xFFEADDFF)
    }
    val colors = if (dark) darkColorScheme(primary = primary, onPrimary = background, primaryContainer = container,
        onPrimaryContainer = primary, surface = background, background = background, onSurface = Color(0xFFE9ECE8),
        onBackground = Color(0xFFE9ECE8), onSurfaceVariant = Color(0xFFBAC3BC), surfaceVariant = container,
        secondaryContainer = container, onSecondaryContainer = Color(0xFFE9ECE8))
    else lightColorScheme(primary = primary, onPrimary = Color.White, primaryContainer = container,
        onPrimaryContainer = primary, surface = background, background = background, onSurface = Color(0xFF202722),
        onBackground = Color(0xFF202722), onSurfaceVariant = Color(0xFF56635B), surfaceVariant = container,
        secondaryContainer = container, onSecondaryContainer = primary)
    val radius = when (style) { DesignStyle.NATURE -> 22.dp; DesignStyle.MONO -> 3.dp; DesignStyle.MATERIAL -> 32.dp }
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(radius), large = RoundedCornerShape(radius), extraLarge = RoundedCornerShape(radius)), content = content)
}
