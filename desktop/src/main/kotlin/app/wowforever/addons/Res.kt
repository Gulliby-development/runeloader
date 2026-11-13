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
