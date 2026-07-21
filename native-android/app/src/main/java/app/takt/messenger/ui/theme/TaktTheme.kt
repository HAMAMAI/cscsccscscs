package app.takt.messenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

private val TaktColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF332866),
    onPrimaryContainer = TextMain,
    secondary = Mint,
    background = Ink,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextMuted,
    outline = Stroke,
    error = Rose,
)

@Composable
fun TaktTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TaktColors, content = content)
}
