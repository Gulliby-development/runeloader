package app.wowforever.addons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

enum class Tab(val label: String, val icon: String) {
    LIBRARY("nav_installed", "ic_extension"),
    BROWSE("nav_browse", "ic_search"),
    SETTINGS("nav_settings", "ic_settings"),
}

/**
 * What the screens share: the tab, the game folder, the Library rows and the running installs.
 * The rules live in core (Library, Updater); this class only runs them off the UI thread.
 */
class AppState(val store: Store) {
    private val scope = MainScope()
    /** Installs and removes run one at a time, like the ops thread on Android. */
    private val ops = Executors.newSingleThreadExecutor { r -> Thread(r, "ops").apply { isDaemon = true } }.asCoroutineDispatcher()

    var tab by mutableStateOf(Tab.LIBRARY)
    var gameRoot by mutableStateOf(store.gameRoot())
        private set
    var rows by mutableStateOf<List<Model.Installed>>(emptyList())
        private set
    var scanned by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    /** Bumped when entries change in place (check results), so the screens read them again. */
    var revision by mutableStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
    var found by mutableStateOf<List<File>>(emptyList())
        private set

    /** Item key -> what its install is doing now. */
    val busy = mutableStateMapOf<String, String>()

    var selectedRow by mutableStateOf<Model.Installed?>(null)
    var selectedItem by mutableStateOf<Model.Item?>(null)
    var confirmRemove by mutableStateOf<Model.Installed?>(null)

    private var checkedOnce = false

    val gameReady: Boolean get() = gameRoot != null && store.gameReady()
    val updates: Int get() { revision; return Library.updates(store) }
    val managed: Int get() { revision; return store.list().size }

    fun start() = rescan()

    fun rescan() {
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { Library.scan(store) }
            Library.carryOver(rows, fresh)
            rows = fresh
            scanned = true
            selectedRow = selectedRow?.let { old -> Library.all(fresh).firstOrNull { same(it, old) } }
            revision++
            if (!checkedOnce && store.list().isNotEmpty()) { checkedOnce = true; checkUpdates(false) }
        }
    }

    private fun same(a: Model.Installed, b: Model.Installed): Boolean =
        if (a.entry != null || b.entry != null) a.entry?.key == b.entry?.key
        else a.main()?.folder == b.main()?.folder

    fun checkUpdates(manual: Boolean) {
        if (checking) return
        if (Library.checkable(store).isEmpty()) { if (manual) message = t("no_managed_addons"); return }
        checking = true
        scope.launch {
            withContext(Dispatchers.IO) { Library.checkAll(store, manual) }
            checking = false
            revision++
            val n = Library.updates(store)
            if (manual || n > 0) message = if (n == 0) t("all_up_to_date") else q("updates_available", n)
        }
    }

    fun install(item: Model.Item) {
        if (!gameReady) { message = t("game_not_set"); tab = Tab.SETTINGS; return }
        val key = item.key()
        if (busy.containsKey(key)) return
        busy[key] = t("queued")
        scope.launch {
            val parts = Library.parts(store, item).size
            val result = withContext(ops) {
                try {
                    Result.success(Library.install(store, item) { i, text, _ ->
                        val shown = if (i.key() == key) text else i.name + "  ·  " + text
                        scope.launch { if (busy.containsKey(key)) busy[key] = shown }
                    })
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            busy.remove(key)
            result.fold(
                onSuccess = { names ->
                    val shown = when {
                        parts > 1 -> q("package_installed", parts - 1, item.name, parts - 1)
                        names.size > 1 -> names.joinToString(", ")
                        else -> item.name
                    }
                    message = t("installed_names", shown)
                },
                onFailure = { message = t("install_failed", item.name) + ": " + Texts.friendly(it) },
            )
            rescan()
        }
    }

    fun update(e: Model.Entry) = install(Sources.itemFor(e))

    fun updateRow(r: Model.Installed) {
        for (m in r.withModules()) m.entry?.let { if (it.updateAvailable) update(it) }
    }

    fun updateAll() {
        var n = 0
        for (e in store.list()) if (e.updateAvailable && !busy.containsKey(e.key)) { update(e); n++ }
        if (n > 0) message = t("updating_n", n)
    }

    fun remove(r: Model.Installed) {
        confirmRemove = null
        if (selectedRow?.let { same(it, r) } == true) selectedRow = null
        message = t("removing", r.title)
        scope.launch {
            val error = withContext(ops) {
                try { Library.remove(store, r); null } catch (e: Exception) { Texts.friendly(e) }
            }
            message = error ?: t("removed", r.title)
            rescan()
        }
    }

    /** The managed entry of an item, when it is installed. */
    fun entryOf(item: Model.Item): Model.Entry? { revision; return store.entry(item.key()) }

    fun setGame(folder: File) {
        val root = Store.normalizeRoot(folder)
        if (root == null) {
            message = t("settings_game_missing")
            return
        }
        store.setGameRoot(root.absolutePath)
        store.setAddonsDirect(false)
        store.load()
        gameRoot = root.absolutePath
        found = emptyList()
        selectedRow = null
        message = t("game_folder_set", root.absolutePath)
        rescan()
    }

    /** Looks for installations; one is set at once, several are listed for the player to pick. */
    fun detect() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { store.detect() }
            when (list.size) {
                0 -> message = t("game_not_found_title")
                1 -> setGame(list[0])
                else -> { found = list; tab = Tab.SETTINGS }
            }
        }
    }
}
