package app.wowforever.addons

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The brand palette, the same as Ui.java on Android. */
object Palette {
    val bg = Color(0xFF0A1513)
    val rail = Color(0xFF0D1B18)
    val surface = Color(0xFF11221E)
    val surface2 = Color(0xFF162B26)
    val surface3 = Color(0xFF1D3730)
    val stroke = Color(0xFF24423A)
    val text = Color(0xFFF3EAD6)
    val text2 = Color(0xFFAEBBB2)
    val text3 = Color(0xFF6E847C)
    val gold = Color(0xFFD9A441)
    val goldInk = Color(0xFF1B1405)
    val rune = Color(0xFF6FE3C6)
    val blue = Color(0xFF7DB8E8)
    val warn = Color(0xFFE8944A)
    val danger = Color(0xFFE86A5F)
}

@Composable
fun RuneloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.gold, onPrimary = Palette.goldInk,
            secondary = Palette.rune, onSecondary = Palette.bg,
            background = Palette.bg, onBackground = Palette.text,
            surface = Palette.surface, onSurface = Palette.text,
            surfaceVariant = Palette.surface2, onSurfaceVariant = Palette.text2,
            outline = Palette.stroke, error = Palette.danger,
        ),
        content = content,
    )
}
