package app.wowforever.addons

import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Reads the Android string resources (res/values-<lang>/strings.xml, copied into the jar by the build),
 * so both apps share one set of translations. English is the fallback for a missing key.
 */
class DesktopTexts(lang: String = systemLanguage()) : Texts.Source {
    private val base = load("values")
    private val local = if (lang == "en") emptyMap() else load("values-$lang")
    private val loc: Locale = Locale.forLanguageTag(lang)

    override fun get(key: String, vararg args: Any?): String {
        val raw = local[key] ?: base[key] ?: return key
        return if (args.isEmpty()) raw else try { String.format(loc, raw, *args) } catch (e: Exception) { raw }
    }

    override fun locale(): Locale = loc

    companion object {
        /** The languages with a translation; the same list as Lang.TAGS on Android. */
        val LANGUAGES = listOf("en", "de", "es", "fr", "pl", "pt", "ru", "zh")

        fun systemLanguage(): String = Locale.getDefault().language.takeIf { it in LANGUAGES } ?: "en"

        private fun load(folder: String): Map<String, String> {
            val stream = DesktopTexts::class.java.getResourceAsStream("/res/$folder/strings.xml") ?: return emptyMap()
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = false
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val doc = stream.use { f.newDocumentBuilder().parse(it) }
            val out = HashMap<String, String>()
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                out[e.getAttribute("name")] = unescape(e.textContent)
            }
            return out
        }

        /** Android resource escaping: \n, \t, \', \", \@, \uXXXX, and a value in double quotes kept as is. */
        fun unescape(s: String): String {
            var t = s
            val quoted = t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")
            if (quoted) t = t.substring(1, t.length - 1)
            else t = t.trim().replace(Regex("\\s+"), " ")
            val b = StringBuilder()
            var i = 0
            while (i < t.length) {
                val c = t[i]
                if (c == '\\' && i + 1 < t.length) {
                    val n = t[i + 1]
                    when (n) {
                        'n' -> b.append('\n')
                        't' -> b.append('\t')
                        'u' -> if (i + 5 < t.length) {
                            b.append(t.substring(i + 2, i + 6).toInt(16).toChar()); i += 4
                        } else b.append(n)
                        else -> b.append(n)
                    }
                    i += 2
                } else if (c == '"' && !quoted) {
                    i++ // unescaped quotes only group text on Android
                } else {
                    b.append(c); i++
                }
            }
            return b.toString()
        }
    }
}
