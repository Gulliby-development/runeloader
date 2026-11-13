package app.wowforever.addons

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

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
    val goldSoft = Color(0x29D9A441)
    val rune = Color(0xFF6FE3C6)
    val runeSoft = Color(0x226FE3C6)
    val blue = Color(0xFF7DB8E8)
    val blueSoft = Color(0x247DB8E8)
    val warn = Color(0xFFE8944A)
    val warnSoft = Color(0x24E8944A)
    val danger = Color(0xFFE86A5F)
    val dangerSoft = Color(0x26E86A5F)
}

/**
 * The bundled fonts of the design (SIL Open Font License, see resources/fonts): Inter for the text,
 * Manrope for titles, JetBrains Mono for versions and small labels. All have Latin and Cyrillic;
 * Chinese falls back to the system font.
 */
object Fonts {
    private fun font(file: String, weight: FontWeight) =
        Font("rl-$file", Fonts::class.java.getResourceAsStream("/fonts/$file.ttf")!!.use { it.readBytes() }, weight)

    val body = FontFamily(
        font("Inter-Regular", FontWeight.Normal),
        font("Inter-Medium", FontWeight.Medium),
        font("Inter-SemiBold", FontWeight.SemiBold),
        font("Inter-Bold", FontWeight.Bold),
    )
    val display = FontFamily(
        font("Manrope-SemiBold", FontWeight.SemiBold),
        font("Manrope-Bold", FontWeight.Bold),
    )
    val mono = FontFamily(
        font("JetBrainsMono-Regular", FontWeight.Normal),
        font("JetBrainsMono-Medium", FontWeight.Medium),
        font("JetBrainsMono-SemiBold", FontWeight.SemiBold),
    )
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
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Fonts.body, color = Palette.text), content = content)
    }
}
