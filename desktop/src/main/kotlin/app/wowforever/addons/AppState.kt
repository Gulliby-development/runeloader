package app.wowforever.addons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

enum class Tab(val label: String, val icon: String) {
    LIBRARY("nav_installed", "ic_extension"),
    BROWSE("nav_browse", "ic_search"),
    SETTINGS("nav_settings", "ic_settings"),
}

enum class BrowseMode(val label: String, val icon: String) {
    POPULAR("browse_mode_popular", "ic_trending"),
    CATEGORIES("browse_mode_categories", "ic_apps"),
    FAVORITES("browse_mode_favorites", "ic_star"),
}

/**
 * What the screens share: the tab, the game folder, the Library rows, the Browse list and the running
 * installs. The rules live in core (Library, Search, Updater); this class only runs them off the UI thread.
 */
class AppState(val store: Store, private val prefs: Platform.Prefs) {
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
    /** Bumped when entries change in place (check results, favorites), so the screens read them again. */
    var revision by mutableStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
    var found by mutableStateOf<List<File>>(emptyList())
        private set
    /** The size of the loaded index, 0 until it is there. */
    var indexSize by mutableStateOf(0)
        private set
    /** The UI language tag, "" for the system language; the window rebuilds when it changes. */
    var language by mutableStateOf(prefs.getString(LANG_KEY, "") ?: "")
        private set

    /** Item key -> what its install is doing now. */
    val busy = mutableStateMapOf<String, String>()
    /** Packages whose modules the Library shows, by row id. */
    val expanded = mutableStateMapOf<String, Boolean>()

    var selectedRow by mutableStateOf<Model.Installed?>(null)
    var selectedItem by mutableStateOf<Model.Item?>(null)
    var confirmRemove by mutableStateOf<Model.Installed?>(null)

    // Library filter
    var libraryQuery by mutableStateOf("")
    var libraryUpdatesOnly by mutableStateOf(false)

    // Browse
    var browseMode by mutableStateOf(BrowseMode.POPULAR)
        private set
    var category by mutableStateOf<String?>(null)
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Model.Item>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var resultError by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var counts by mutableStateOf<Map<String, Int>?>(null)
        private set
    private var searchJob: Job? = null
    private var searched = false

    private var checkedOnce = false

    val gameReady: Boolean get() = gameRoot != null && store.gameReady()
    val updates: Int get() { revision; return Library.updates(rows) }
    val managed: Int get() { revision; return store.list().size }
    val verifiedOnly: Boolean get() { revision; return store.verifiedOnly() }

    /** Scans with the cached index at once, then loads a fresh index and scans again, so manual installs are recognized. */
    fun start() {
        rescan()
        scope.launch {
            val n = withContext(Dispatchers.IO) { try { Index.items(false).size } catch (e: Exception) { 0 } }
            indexSize = n
            if (n > 0) rescan()
        }
    }

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

    private fun same(a: Model.Installed, b: Model.Installed): Boolean = rowId(a) == rowId(b) ||
        (a.entry != null && b.entry == null && b.catalogMatch != null && a.entry.key == b.catalogMatch.key())

    fun checkUpdates(manual: Boolean) {
        if (checking) return
        if (Library.checkable(store).isEmpty()) { if (manual) message = t("no_managed_addons"); return }
        checking = true
        scope.launch {
            withContext(Dispatchers.IO) { Library.checkAll(store, manual) }
            checking = false
            revision++
            val n = updates
            if (manual || n > 0) message = if (n == 0) t("all_up_to_date") else q("updates_available", n)
        }
    }

    /** Asks for the latest version of one addon. */
    fun checkOne(e: Model.Entry) {
        message = t("checking_one", e.name)
        scope.launch {
            val (latest, err) = withContext(Dispatchers.IO) {
                try { Updater.latestVersion(e) to null } catch (x: Exception) { null to Texts.friendly(x) }
            }
            Library.applyCheck(e, latest, null, err)
            revision++
            message = err ?: if (e.updateAvailable) t("version_available", latest) else t("latest_installed")
        }
    }

    /** @param alone take over an addon installed by hand: no modules of a package come with it. */
    fun install(item: Model.Item, alone: Boolean = false) {
        if (!gameReady) { message = t("game_not_set"); tab = Tab.SETTINGS; return }
        val key = item.key()
        if (busy.containsKey(key)) return
        busy[key] = t("queued")
        scope.launch {
            val parts = Library.parts(store, item, alone).size
            val result = withContext(ops) {
                try {
                    Result.success(Library.install(store, item, alone) { i, text, _ ->
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

    /** Updates every part of a package (or a single row) that has an update. */
    fun updateRow(r: Model.Installed) {
        // One button for the whole package: managed parts update, parts installed by hand that the index
        // has in a newer version are taken over with that version.
        for (m in r.withModules()) {
            val e = m.entry
            if (e != null) { if (e.updateAvailable && !m.missing && !busy.containsKey(e.key)) update(e) }
            else if (Library.untrackedUpdate(m) != null) install(m.catalogMatch, alone = true)
        }
    }

    fun updateAll() {
        var n = 0
        for (e in store.list()) if (e.updateAvailable && !busy.containsKey(e.key)) { update(e); n++ }
        for (r in rows) for (m in r.withModules()) if (Library.untrackedUpdate(m) != null && !busy.containsKey(m.catalogMatch.key())) { install(m.catalogMatch, alone = true); n++ }
        if (n > 0) message = t("updating_n", n)
    }

    /** Lets Runeloader manage a manual install it found on WoWInterface. */
    fun adoptWowi(id: String, r: Model.Installed) {
        val it = Model.Item()
        it.source = Model.SRC_WOWI
        it.id = id
        it.name = r.title
        it.author = r.author ?: ""
        it.pageUrl = "https://www.wowinterface.com/downloads/info$id"
        install(it)
    }

    fun installZip(zip: File) {
        if (!gameReady) { message = t("game_not_set"); tab = Tab.SETTINGS; return }
        val key = "zip:" + zip.name
        if (busy.containsKey(key)) return
        busy[key] = t("copying")
        scope.launch {
            val result = withContext(ops) {
                try { Result.success(Library.installZip(store, zip)) } catch (e: Exception) { Result.failure(e) }
            }
            busy.remove(key)
            message = result.fold({ t("zip_installed", joinShort(it, 4)) }, { t("zip_install_failed") + ": " + Texts.friendly(it) })
            rescan()
        }
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

    /** Asks before removing a managed entry, from its row when the Library has one. */
    fun removeEntry(e: Model.Entry) {
        confirmRemove = Library.all(rows).firstOrNull { it.entry?.key == e.key }
            ?: Model.Installed().also { it.entry = e; it.title = e.name }
    }

    /** The managed entry of an item, when it is installed. */
    fun entryOf(item: Model.Item): Model.Entry? { revision; return store.entry(item.key()) }

    fun isFavorite(item: Model.Item): Boolean { revision; return store.isFavorite(item.key()) }

    fun toggleFavorite(item: Model.Item) {
        val now = store.toggleFavorite(item)
        message = t(if (now) "favorite_added" else "favorite_removed")
        revision++
        if (browseMode == BrowseMode.FAVORITES) search(false)
    }

    /** Shows an addon in Browse, such as the successor of a discontinued one. */
    fun showItem(item: Model.Item) {
        tab = Tab.BROWSE
        selectedItem = item
    }

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

    fun changeLanguage(tag: String) {
        prefs.putString(LANG_KEY, tag)
        Texts.source = DesktopTexts(tag.ifEmpty { DesktopTexts.systemLanguage() })
        language = tag
        counts = null
        revision++
    }

    fun clearCache() {
        val cache = File(DesktopPlatform.appHome(), "cache")
        for (name in listOf("dl", "wowi-filelist.json", "index.json", "index.etag")) Installer.deleteRecursive(File(cache, name))
        Images.clearDisk()
        counts = null
        message = t("cache_cleared")
    }

    /** Fills the state without the disk or the network, for the screenshots the tests render. */
    fun preview(rows: List<Model.Installed>, results: List<Model.Item>, indexSize: Int) {
        this.rows = rows
        this.results = results
        this.indexSize = indexSize
        scanned = true
        searched = true
        checkedOnce = true
        revision++
    }

    // ── Browse ──

    /** The category grid shows in Categories mode until a category is open or a query is typed. */
    val showGrid: Boolean get() = browseMode == BrowseMode.CATEGORIES && category == null && query.isBlank()

    fun setBrowseMode(mode: BrowseMode, cat: String? = null) {
        if (mode == browseMode && cat == category && searched) return
        browseMode = mode
        category = cat
        selectedItem = null
        search(false)
    }

    fun changeQuery(q: String) {
        if (q == query) return
        query = q
        search(false, delayMs = 250)
    }

    fun setVerifiedOnly(on: Boolean) {
        store.setVerifiedOnly(on)
        counts = null
        revision++
        message = t(if (on) "filter_verified_on" else "filter_verified_off")
        search(false)
    }

    /** The first search of the Browse tab. */
    fun ensureResults() { if (!searched) search(false) }

    fun loadCounts() {
        if (counts != null) return
        scope.launch {
            val got = withContext(Dispatchers.IO) { try { Search.counts(store.verifiedOnly()) } catch (e: Exception) { null } }
            counts = got ?: emptyMap()
        }
    }

    /** Runs the Browse search; more = the next page of the same one. */
    fun search(more: Boolean, delayMs: Long = 0) {
        searched = true
        if (showGrid) { searchJob?.cancel(); loading = false; loadCounts(); return }
        if (more && (loading || !hasMore)) return
        val q = query
        val cat = if (browseMode == BrowseMode.FAVORITES) null else category
        val favs = browseMode == BrowseMode.FAVORITES
        val verified = store.verifiedOnly()
        val offset = if (more) results.size else 0
        searchJob?.cancel()
        loading = true
        resultError = null
        if (!more) { results = emptyList(); hasMore = false }
        searchJob = scope.launch {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            try {
                val (got, moreAvail, warn) = withContext(Dispatchers.IO) {
                    val repo = if (q.contains("/")) Sources.GitHub.normalize(q) else null
                    when {
                        favs -> Triple(if (more) emptyList() else favorites(q), false, null)
                        repo != null -> {
                            // A pasted owner/repo or link: its index entry if listed, else the repository itself.
                            try { Index.items(false) } catch (e: Exception) { }
                            Triple(listOf(Index.item(repo) ?: Sources.GitHub.info(repo)), false, null)
                        }
                        else -> {
                            val page = Search.page(q, cat, verified, offset, PAGE)
                            Triple(page.items, page.more, page.warning)
                        }
                    }
                }
                results = if (more) results + got else got
                hasMore = moreAvail
                if (warn != null && !more) message = warn
                if (!more && got.size == 1 && q.contains("/")) selectedItem = got[0]
                if (indexSize == 0) indexSize = try { Index.items(false).size } catch (e: Exception) { 0 }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (more) message = Texts.friendly(e) else resultError = Texts.friendly(e)
            }
            loading = false
        }
    }

    /** The starred addons, with fresh index data when the index has them; the query filters by name, author or summary. */
    private fun favorites(q: String): List<Model.Item> {
        Index.loadCached()
        val needle = q.trim().lowercase(Locale.ROOT)
        return store.favorites().map { f -> (if (f.source == Model.SRC_GITHUB) Index.item(f.id) else null) ?: f }
            .filter { needle.isEmpty() || "${it.name}\n${it.author}\n${it.summary}".lowercase(Locale.ROOT).contains(needle) }
    }

    companion object {
        const val LANG_KEY = "language"
        const val PAGE = 60
    }
}

/** A stable id of a Library row: its entry, its catalog match, or its main folder. */
fun rowId(r: Model.Installed): String =
    r.entry?.key ?: r.catalogMatch?.key() ?: ("dir:" + (r.main()?.folder ?: r.title))

/** The key of the install that a row is waiting for, if any. */
fun busyKey(r: Model.Installed): String? = r.entry?.key ?: r.catalogMatch?.key()

fun anyUpdate(r: Model.Installed): Boolean = r.withModules().any { Library.needsUpdate(it) }

fun joinShort(l: List<String>, max: Int): String =
    if (l.size <= max) l.joinToString(", ") else t("join_more", l.take(max).joinToString(", "), l.size - max)

fun sourceName(src: String?): String = when (src) {
    Model.SRC_WOWI -> "WoWInterface"
    Model.SRC_ZIP -> t("source_zip")
    else -> "GitHub"
}

fun formatCount(n: Long): String {
    if (n < 0) return ""
    fun dec(v: Double) = java.text.NumberFormat.getNumberInstance(Texts.locale()).apply {
        maximumFractionDigits = 1; isGroupingUsed = false
    }.format(v)
    return when {
        n >= 1_000_000 -> t("fmt_millions", dec(n / 1_000_000.0))
        n >= 1_000 -> t("fmt_thousands", dec(n / 1_000.0))
        else -> n.toString()
    }
}

fun formatDate(ms: Long): String {
    if (ms <= 0) return ""
    val days = (System.currentTimeMillis() - ms) / (24L * 3600 * 1000)
    return when {
        days < 1 -> t("today")
        days == 1L -> t("yesterday")
        days < 30 -> q("days_ago", days.toInt())
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Texts.locale()).format(java.util.Date(ms))
    }
}

/** The index entry of a GitHub addon, so catalog and installed cards get its metadata too. */
fun indexMeta(source: String?, id: String?, fallback: Model.Item?): Model.Item {
    if (fallback != null && fallback.description.isNotEmpty()) return fallback
    val ix = if (source == Model.SRC_GITHUB && id != null) Index.item(id) else null
    return ix ?: fallback ?: Model.Item()
}
