package app.aaina

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF4FC3B0), onPrimary = Color(0xFF00332B),
        background = Color(0xFF13262C), surface = Color(0xFF13262C), surfaceVariant = Color(0xFF1A3139),
        onBackground = Color(0xFFDCE8E5), onSurface = Color(0xFFDCE8E5), onSurfaceVariant = Color(0xFF8AA0A6),
        secondaryContainer = Color(0xFF2C474F), onSecondaryContainer = Color(0xFFDCE8E5), outline = Color(0xFF2C474F)
    ) else lightColorScheme(
        primary = Color(0xFF1F7A6D), onPrimary = Color.White,
        background = Color(0xFFF1F5F4), surface = Color(0xFFF1F5F4), surfaceVariant = Color.White,
        onBackground = Color(0xFF10242B), onSurface = Color(0xFF10242B), onSurfaceVariant = Color(0xFF5B7077),
        secondaryContainer = Color(0xFFCFE6E1), onSecondaryContainer = Color(0xFF10242B), outline = Color(0xFFC9D4D1)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
