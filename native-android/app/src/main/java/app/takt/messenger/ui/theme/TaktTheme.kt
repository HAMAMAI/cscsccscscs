package app.takt.messenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.takt.messenger.data.AppearanceSettings
import app.takt.messenger.data.ThemeMode

val Ink = Color(0xFF0E0B1C)
val Panel = Color(0xFF17132A)
val PanelRaised = Color(0xFF211B39)
val Purple = Color(0xFF8C78FF)
val PurpleSoft = Color(0xFFB6A9FF)
val Mint = Color(0xFF58D6A8)
val Rose = Color(0xFFFF817A)
val TextMain = Color(0xFFF8F6FF)
val TextMuted = Color(0xFFAAA3C0)
val Stroke = Color(0xFF332B4E)

private fun darkColors(accent: Color, midnight: Boolean) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = .28f),
    onPrimaryContainer = TextMain,
    secondary = Mint,
    background = if (midnight) Color(0xFF070A16) else Ink,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextMuted,
    outline = Stroke,
    error = Rose,
)

private fun lightColors(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = .2f),
    onPrimaryContainer = Color(0xFF201A36),
    secondary = Color(0xFF14785A),
    background = Color(0xFFF9F7FF),
    onBackground = Color(0xFF1B1725),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1B1725),
    surfaceVariant = Color(0xFFE9E1F0),
    onSurfaceVariant = Color(0xFF4B4457),
    outline = Color(0xFF7A7284),
    error = Color(0xFFB3261E),
)

@Composable
fun TaktTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val accent = runCatching { Color(android.graphics.Color.parseColor(settings.accentHex)) }.getOrDefault(Purple)
    val colors = when (settings.themeMode) {
        ThemeMode.Light -> lightColors(accent)
        ThemeMode.Dark -> darkColors(accent, midnight = false)
        ThemeMode.Midnight -> darkColors(accent, midnight = true)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
