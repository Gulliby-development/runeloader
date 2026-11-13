package app.wowforever.addons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Addon logos and screenshots: a memory cache over a disk cache in the app's cache folder, loaded off
 * the UI thread, as ImageLoader on Android. The index makes the logos, the app only shows a ready image.
 */
object Images {
    private val mem = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 160
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(4)
    private val dir: File by lazy { File(DesktopPlatform.appHome(), "cache/img").also { it.mkdirs() } }

    @Synchronized fun cached(url: String): ImageBitmap? = mem[url]

    suspend fun load(url: String): ImageBitmap? = withContext(io) {
        cached(url)?.let { return@withContext it }
        try {
            val f = File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length + ".img")
            if (!f.isFile || f.length() == 0L) {
                val tmp = File(f.path + ".part")
                Http.download(url, null, tmp, null)
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            }
            val img = decode(f.readBytes())
            if (img == null) f.delete() else synchronized(this@Images) { mem[url] = img }
            img
        } catch (e: Exception) {
            null
        }
    }

    fun decode(bytes: ByteArray): ImageBitmap? =
        try { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() } catch (e: Exception) { null }

    /** A PNG shipped in the jar, such as the category icons from res/drawable-nodpi. */
    fun resource(path: String): ImageBitmap? = synchronized(this) {
        mem["res:$path"] ?: Images::class.java.getResourceAsStream("/$path")?.use { decode(it.readBytes()) }?.also { mem["res:$path"] = it }
    }

    fun clearDisk() { Installer.deleteRecursive(dir); synchronized(this) { mem.keys.removeAll { !it.startsWith("res:") } } }
}

/** The image at the URL, null until it has loaded (or for good when it fails). */
@Composable
fun rememberUrlImage(url: String?): ImageBitmap? {
    if (url.isNullOrEmpty()) return null
    var img by remember(url) { mutableStateOf(Images.cached(url)) }
    LaunchedEffect(url) { if (img == null) img = Images.load(url) }
    return img
}

/** An addon logo with rounded corners; the first letter of its name until the logo loads. */
@Composable
fun AddonIcon(url: String?, name: String?, size: Dp = 40.dp) {
    val img = rememberUrlImage(url)
    val shape = RoundedCornerShape(size * 0.26f)
    if (img != null) {
        Box(Modifier.size(size).clip(shape).background(Palette.surface3).border(1.dp, Palette.stroke, shape)) {
            Image(img, null, Modifier.size(size), contentScale = ContentScale.Crop)
        }
        return
    }
    val letter = name?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    val tint = LETTER_TINTS[Math.floorMod((name ?: "").hashCode(), LETTER_TINTS.size)]
    Box(Modifier.size(size).clip(shape).background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.82f)))),
        contentAlignment = Alignment.Center) {
        Text(letter.toString(), color = Palette.bg, fontFamily = Fonts.display, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.Bold)
    }
}

/** Soft tints of the letter icons, as in the design; a name always gets the same one. */
private val LETTER_TINTS = listOf(
    Color(0xFFE3A83F), Color(0xFF7FB2E5), Color(0xFFE8685A), Color(0xFF6EE0C4), Color(0xFFA9C98A),
    Color(0xFFC9A3F0), Color(0xFFEBC27E), Color(0xFF8FB0E8), Color(0xFFE8968A), Color(0xFFE8914A),
)

/** The icon of a Browse category tile (cat_<key>.png, shared with Android). */
@Composable
fun CategoryIcon(key: String, size: Dp) {
    val img = remember(key) { Images.resource("res/drawable-nodpi/cat_$key.png") ?: Images.resource("res/drawable-nodpi/cat_other.png") }
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Palette.surface3), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.size(size), contentScale = ContentScale.Fit)
    }
}
