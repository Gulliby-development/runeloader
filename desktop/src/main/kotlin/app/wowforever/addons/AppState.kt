package app.wowforever.addons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

enum class Tab(val label: String, val icon: String) {
    LIBRARY("nav_installed", "ic_extension"),
    BROWSE("nav_browse", "ic_search"),
    SETTINGS("nav_settings", "ic_settings"),
}

/** What the screens share: the tab, the game folder and the managed addons. */
class AppState(val store: Store) {
    var tab by mutableStateOf(Tab.LIBRARY)
    var gameRoot by mutableStateOf(store.gameRoot())
        private set
    var entries by mutableStateOf(sorted(store.list()))
        private set
    var message by mutableStateOf<String?>(null)

    val gameReady: Boolean get() = gameRoot != null && store.gameReady()

    fun setGame(folder: File) {
        val root = Store.normalizeRoot(folder)
        if (root == null) {
            message = Texts.s("settings_game_missing")
            return
        }
        store.setGameRoot(root.absolutePath)
        store.setAddonsDirect(false)
        store.load()
        gameRoot = root.absolutePath
        entries = sorted(store.list())
        message = Texts.s("game_folder_set", root.absolutePath)
    }

    /** The found installations; one is set at once, several are left to the player. */
    fun detect(): List<File> {
        val found = store.detect()
        if (found.isEmpty()) message = Texts.s("game_not_found_title")
        else if (found.size == 1) setGame(found[0])
        return found
    }

    private fun sorted(list: List<Model.Entry>) = list.sortedBy { (it.name ?: it.key).lowercase() }
}
