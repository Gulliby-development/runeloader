package app.wowforever.addons

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the main screens with sample data into build/shots (PNG, and a small JPEG), so a change to
 * the look can be compared with the design without a Windows machine. CI keeps them as an artifact.
 */
class ScreenshotTest {
    private fun toc(addons: File, folder: String, title: String, version: String, author: String, iface: String = "16001") {
        val d = File(addons, folder).also { it.mkdirs() }
        File(d, "$folder.toc").writeText("## Interface: $iface\n## Title: $title\n## Version: $version\n## Author: $author\n")
    }

    private fun entry(store: Store, id: String, name: String, author: String, version: String, latest: String?, vararg folders: String) {
        val e = Model.Entry()
        e.source = Model.SRC_GITHUB; e.id = id; e.key = "github:$id"
        e.name = name; e.author = author; e.version = version
        e.folders = folders.toMutableList()
        e.installedAt = System.currentTimeMillis() - 3 * 24 * 3600 * 1000L
        store.put(e)
        Library.applyCheck(e, latest ?: version, if (latest != null) "### Changes\n- Fixed quest points in Durotar\n- New data for Forever 1.16.1\n- Lighter map opening" else null, null)
    }

    private fun item(id: String, name: String, author: String, summary: String, downloads: Long, stars: Long, vararg tags: String) = Model.Item().also {
        it.source = Model.SRC_GITHUB; it.id = id; it.name = name; it.author = author; it.summary = summary
        it.downloads = downloads; it.stars = stars; it.foreverVerified = true; it.version = "1.0.0"
        it.tags = tags.toMutableList(); it.pageUrl = "https://github.com/$id"
    }

    @Test fun renderScreens() {
        val home = Files.createTempDirectory("shots").toFile()
        val game = File(home, "World of Warcraft")
        val addons = File(game, "_forever_/Interface/AddOns")
        toc(addons, "Questie", "Questie", "9.5.2", "Questie Team")
        toc(addons, "Questie-Forever", "Questie Forever data", "9.5.2", "Questie Team")
        toc(addons, "Details", "Details! Damage Meter", "2.1.4", "Tercio")
        toc(addons, "Bagnon", "Bagnon Forever", "10.2.0", "tullamods")
        toc(addons, "Gulli-Toolkit", "Gulli Toolkit", "1.6.0", "Gulliby")
        toc(addons, "Gulli-QuestCleaner", "Gulli QuestCleaner", "1.4.0", "Gulliby")
        toc(addons, "Gulli-XP", "Gulli XP", "1.4.0", "Gulliby")
        toc(addons, "OldMapTweaks", "OldMap Tweaks", "3.0.1", "someone", "11500")
        toc(addons, "Leatrix_Plus", "Leatrix Plus", "1.15.30", "Leatrix")

        val platform = DesktopPlatform(File(home, "app"))
        Texts.source = DesktopTexts("ru")
        val store = Store.get(platform)
        store.setGameRoot(game.absolutePath)
        store.load()
        entry(store, "questie/Questie", "Questie", "Questie Team", "9.5.2", "9.6.1", "Questie", "Questie-Forever")
        entry(store, "Tercio/Details", "Details! Damage Meter", "Tercio", "2.1.4", "2.1.5", "Details")
        entry(store, "tullamods/Bagnon", "Bagnon Forever", "tullamods", "10.2.0", null, "Bagnon")
        entry(store, "Gulliby-development/gulli-toolkit", "Gulli Toolkit", "Gulliby", "1.6.0", null, "Gulli-Toolkit")
        entry(store, "Gulliby-development/gulli-toolkit#QuestCleaner", "Gulli Toolkit: QuestCleaner", "Gulliby", "1.4.0", null, "Gulli-QuestCleaner")
        entry(store, "Gulliby-development/gulli-toolkit#XP", "Gulli Toolkit: XP", "Gulliby", "1.4.0", "1.5.0", "Gulli-XP")
        val rows = Library.scan(store)

        val items = listOf(
            item("questie/Questie", "Questie", "Questie Team", "Quests on the map and in tooltips: where to take, whom to kill, where to hand in.", 48200, 1412),
            item("Tercio/Details", "Details! Damage Meter", "Tercio", "Damage, healing and everything that happened in combat, in handy windows.", 31900, 690),
            item("Gulliby-development/improved", "Improved Forever", "Gulliby", "A set of UI improvements for Forever. One click installs it all.", 12400, 211),
            item("Gulliby-development/gulli-toolkit", "Gulli Toolkit", "Gulliby", "A hub of small helpers: quests, bags, mail, vendor and more.", 2100, 38),
            item("munk/ConsolePort", "ConsolePort Lite", "munk", "Play with a gamepad: action rings, cursor, button hints.", 9800, 402, "gamepad"),
            item("tullamods/Bagnon", "Bagnon Forever", "tullamods", "All bags in one window, item search and the bank.", 8600, 155),
            item("Leatrix/LeatrixPlus", "Leatrix Plus", "Leatrix", "Hundreds of small interface tweaks, each one turns on alone.", 7100, 120),
            item("Hoizame/AtlasLoot", "AtlasLoot Forever", "Hoizame", "Dungeon and raid loot: what drops and from whom.", 6300, 97),
            item("shagu/pfQuest", "pfQuest Forever", "shagu", "Quests, ore and herbs on the map. A light Questie alternative.", 5900, 88),
        )
        val app = AppState(store, platform.prefs())
        app.preview(rows, items, 641)

        val out = File("build/shots").also { it.mkdirs() }
        fun shot(name: String, prepare: () -> Unit) {
            prepare()
            render(out, name) { RuneloaderTheme { Shell(app) } }
        }
        val all = Library.all(rows)
        shot("library") {
            app.tab = Tab.LIBRARY
            all.firstOrNull { it.isPackage }?.let { app.expanded[rowId(it)] = true }
            app.selectedRow = all.firstOrNull { it.title == "Questie" }
        }
        shot("browse") { app.tab = Tab.BROWSE; app.selectedItem = null }
        shot("browse-detail") { app.tab = Tab.BROWSE; app.selectedItem = items[2] }
        shot("settings") { app.tab = Tab.SETTINGS }
        Texts.source = DesktopTexts("en")
        shot("library-en") { app.tab = Tab.LIBRARY; app.selectedRow = all.firstOrNull { it.isPackage } }
        assertTrue(File(out, "library.png").length() > 10_000)
    }

    private fun render(out: File, name: String, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(1280, 800, Density(1f), content = content)
        try {
            var img = scene.render(0)
            for (i in 1..4) img = scene.render(i * 100_000_000L)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            File(out, "$name.jpg").writeBytes(img.encodeToData(EncodedImageFormat.JPEG, 82)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
