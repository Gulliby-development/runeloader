package app.wowforever.addons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadXmlImageVector
import org.xml.sax.InputSource

/** An Android vector drawable from res/drawable, shared with the Android app. */
@Composable
fun drawable(name: String): Painter {
    val density = LocalDensity.current
    val vector = remember(name, density) {
        val stream = AppState::class.java.getResourceAsStream("/res/drawable/$name.xml")
            ?: error("missing drawable $name")
        stream.use { loadXmlImageVector(InputSource(it), density) }
    }
    return rememberVectorPainter(vector)
}

/** A text from the shared string resources. */
fun t(key: String, vararg args: Any?): String = Texts.s(key, *args)

/** A counted text from the shared <plurals>. */
fun q(key: String, n: Int, vararg args: Any?): String =
    (Texts.source as? DesktopTexts)?.q(key, n, *args) ?: key

/** Release notes and descriptions through the shared Markdown renderer, with headings and bold kept. */
fun markdown(md: String): androidx.compose.ui.text.AnnotatedString {
    val doc = Markdown.render(md)
    val b = androidx.compose.ui.text.AnnotatedString.Builder(doc.text.toString())
    val strong = androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Palette.text)
    for (r in doc.headings + doc.bold) if (r[0] < r[1] && r[1] <= doc.text.length) b.addStyle(strong, r[0], r[1])
    return b.toAnnotatedString()
}

/** The system folder picker; null when the player cancels. */
fun pickFolder(): java.io.File? {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        dialogTitle = t("game_where_title")
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** The system file picker for an addon ZIP; null when the player cancels. */
fun pickZip(): java.io.File? {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        dialogTitle = t("action_install_zip")
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("ZIP", "zip")
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Shows a folder in the system file manager. */
fun openFolder(dir: java.io.File) {
    try { if (dir.isDirectory && java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir) } catch (e: Exception) { }
}

fun openUrl(url: String) {
    try { if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } catch (e: Exception) { }
}
