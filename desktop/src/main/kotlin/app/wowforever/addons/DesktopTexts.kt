package app.wowforever.addons

import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Reads the Android string resources (res/values-<lang>/strings.xml, copied into the jar by the build),
 * so both apps share one set of translations. English is the fallback for a missing key.
 */
class DesktopTexts(private val lang: String = systemLanguage()) : Texts.Source {
    private val base = load("values")
    private val local = if (lang == "en") Strings() else load("values-$lang")
    private val loc: Locale = Locale.forLanguageTag(lang)

    override fun get(key: String, vararg args: Any?): String {
        val raw = local.strings[key] ?: base.strings[key] ?: return key
        return format(raw, args)
    }

    /** A counted text from <plurals>, with the language's plural rule; n is also the first argument unless args are given. */
    fun q(key: String, n: Int, vararg args: Any?): String {
        val forms = local.plurals[key] ?: base.plurals[key] ?: return key
        val form = forms[quantity(if (local.plurals.containsKey(key)) lang else "en", n)] ?: forms["other"] ?: return key
        return format(form, if (args.isEmpty()) arrayOf(n) else args)
    }

    private fun format(raw: String, args: Array<out Any?>): String =
        if (args.isEmpty()) raw else try { String.format(loc, raw, *args) } catch (e: Exception) { raw }

    override fun locale(): Locale = loc

    companion object {
        /** The languages with a translation; the same list as Lang.TAGS on Android. */
        val LANGUAGES = listOf("en", "de", "es", "fr", "pl", "pt", "ru", "zh")

        /** Each language named in itself, as Lang.NAMES on Android. */
        val NAMES = linkedMapOf("en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français",
            "pl" to "Polski", "pt" to "Português", "ru" to "Русский", "zh" to "中文（简体）")

        fun systemLanguage(): String = Locale.getDefault().language.takeIf { it in LANGUAGES } ?: "en"

        /** The CLDR plural category of n for the languages the app has. */
        fun quantity(lang: String, n: Int): String {
            val m10 = n % 10
            val m100 = n % 100
            return when (lang) {
                "ru" -> when {
                    m10 == 1 && m100 != 11 -> "one"
                    m10 in 2..4 && m100 !in 12..14 -> "few"
                    else -> "many"
                }
                "pl" -> when {
                    n == 1 -> "one"
                    m10 in 2..4 && m100 !in 12..14 -> "few"
                    else -> "many"
                }
                "fr", "pt" -> if (n == 0 || n == 1) "one" else "other"
                "zh" -> "other"
                else -> if (n == 1) "one" else "other"
            }
        }

        private class Strings(val strings: Map<String, String> = emptyMap(), val plurals: Map<String, Map<String, String>> = emptyMap())

        private fun load(folder: String): Strings {
            val stream = DesktopTexts::class.java.getResourceAsStream("/res/$folder/strings.xml") ?: return Strings()
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
            val plurals = HashMap<String, Map<String, String>>()
            val pl = doc.getElementsByTagName("plurals")
            for (i in 0 until pl.length) {
                val e = pl.item(i) as Element
                val forms = HashMap<String, String>()
                val items = e.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val it = items.item(j) as Element
                    forms[it.getAttribute("quantity")] = unescape(it.textContent)
                }
                plurals[e.getAttribute("name")] = forms
            }
            return Strings(out, plurals)
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
