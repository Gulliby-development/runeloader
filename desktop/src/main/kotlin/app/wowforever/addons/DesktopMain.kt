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
    logErrors()
    try { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) } catch (e: Exception) { }
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
            RuneloaderTheme { Shell(remember { AppState(store).also { it.start() } }) }
        }
    }
}

/** Writes uncaught errors to log.txt in the app folder, so a player can send it with a bug report. */
private fun logErrors() {
    val log = java.io.File(DesktopPlatform.appHome(), "log.txt")
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        try {
            log.appendText("${java.time.Instant.now()} [${t.name}] $APP_VERSION\n${e.stackTraceToString()}\n")
        } catch (ignored: Exception) { }
        e.printStackTrace()
    }
}
