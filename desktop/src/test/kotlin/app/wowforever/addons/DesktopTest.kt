package app.wowforever.addons

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DesktopTest {
    @Test fun textsComeFromTheSharedResources() {
        val en = DesktopTexts("en")
        assertEquals("Browse", en.get("nav_browse"))
        assertEquals("Game folder: C:\\WoW", en.get("game_folder_set", "C:\\WoW"))
        assertEquals("no_such_key", en.get("no_such_key"))
        // Every language has the tab names, and a missing key falls back to English.
        for (lang in DesktopTexts.LANGUAGES) assertNotEquals("nav_browse", DesktopTexts(lang).get("nav_browse"))
        assertNotEquals(en.get("nav_settings"), DesktopTexts("ru").get("nav_settings"))
    }

    @Test fun androidEscapes() {
        assertEquals("It's \"here\"\nnext", DesktopTexts.unescape("It\\'s \\\"here\\\"\\nnext"))
        assertEquals("a b", DesktopTexts.unescape("  a\n   b "))
        assertEquals("  kept  ", DesktopTexts.unescape("\"  kept  \""))
        assertEquals("é", DesktopTexts.unescape("\\u00e9"))
    }

    @Test fun settingsSurviveARestart() {
        val home = Files.createTempDirectory("rl").toFile()
        val a = DesktopPlatform(home).prefs()
        a.putString("gameRoot", "/games/wow")
        a.putLong("lastCheck", 1700000000000L)
        a.putBoolean("verifiedOnly", true)
        a.putString("gone", "x")
        a.remove("gone")
        val b = DesktopPlatform(home).prefs()
        assertEquals("/games/wow", b.getString("gameRoot", null))
        assertEquals(1700000000000L, b.getLong("lastCheck", 0))
        assertEquals(true, b.getBoolean("verifiedOnly", false))
        assertNull(b.getString("gone", null))
        assertEquals(7, b.getInt("missing", 7))
        assertEquals(true, File(home, "settings.json").isFile)
    }
}
