package app.wowforever.addons

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.File
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

/** The version the build wrote into version.txt. */
val APP_VERSION: String = DesktopTexts::class.java.getResource("/version.txt")?.readText()?.trim() ?: "dev"

/**
 * `--smoke=<file>` starts the app as usual, waits until the first scan ran, writes "ok" into the
 * file and exits; CI runs the installed app this way, so a start that crashes fails the build.
 */
fun main(args: Array<String>) {
    val smoke = args.firstOrNull { it.startsWith("--smoke=") }?.removePrefix("--smoke=")?.let(::File)
    logErrors(smoke)
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
            val app = remember { AppState(store).also { it.start() } }
            RuneloaderTheme { Shell(app) }
            if (smoke != null) LaunchedEffect(Unit) {
                withTimeout(60_000) { while (!app.scanned) delay(100) }
                delay(1000)
                smoke.writeText("ok $APP_VERSION\n")
                exitApplication()
            }
        }
    }
}

/** Writes uncaught errors to log.txt in the app folder, so a player can send it with a bug report. */
private fun logErrors(smoke: File?) {
    val log = java.io.File(DesktopPlatform.appHome(), "log.txt")
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        try {
            log.appendText("${java.time.Instant.now()} [${t.name}] $APP_VERSION\n${e.stackTraceToString()}\n")
        } catch (ignored: Exception) { }
        e.printStackTrace()
        if (smoke != null) {
            try { smoke.writeText("error\n${e.stackTraceToString()}") } catch (ignored: Exception) { }
            Runtime.getRuntime().halt(1)
        }
    }
}
