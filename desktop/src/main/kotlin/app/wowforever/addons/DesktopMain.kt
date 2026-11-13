package app.wowforever.addons

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

/** The version the build wrote into version.txt. */
val APP_VERSION: String = DesktopTexts::class.java.getResource("/version.txt")?.readText()?.trim() ?: "dev"

fun main() {
    Texts.source = DesktopTexts()
    val store = Store.get(DesktopPlatform())
    store.load()
    application {
        val state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = Texts.s("app_name"),
            icon = painterResource("icon.png"),
        ) {
            window.minimumSize = Dimension(900, 600)
            RuneloaderTheme { Shell(remember { AppState(store) }) }
        }
    }
}
