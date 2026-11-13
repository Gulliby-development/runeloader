package app.wowforever.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

// ── Library ──

@Composable
fun LibraryScreen(app: AppState) {
    app.revision
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Header(t("library_title"), if (app.managed == 0) null else t("installed_managed", app.managed)) {
                if (app.gameReady && app.managed > 0) {
                    if (app.updates > 0) {
                        Button(t("action_update_all_plain"), primary = true, icon = "ic_upgrade") { app.updateAll() }
                        Spacer(Modifier.width(10.dp))
                    }
                    Button(if (app.checking) t("action_checking") else t("action_check"), icon = "ic_refresh", enabled = !app.checking) { app.checkUpdates(true) }
                }
            }
            when {
                !app.gameReady -> GameMissing(app)
                !app.scanned -> Unit
                app.rows.isEmpty() -> Empty("ic_extension", t("installed_empty_title"), t("installed_empty_text")) {
                    Button(t("action_open_catalog"), primary = true, icon = "ic_search") { app.tab = Tab.BROWSE }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(Library.all(app.rows)) { r -> LibraryRow(app, r) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        if (app.gameReady && app.rows.isNotEmpty()) DetailPane { RowDetail(app, app.selectedRow) }
    }
}

@Composable
private fun LibraryRow(app: AppState, r: Model.Installed) {
    val e = r.entry
    val title = (if (r.parent != null) "    " else "") + (r.title ?: r.main()?.folder ?: "?")
    val sub = listOfNotNull(r.author?.takeIf { it.isNotEmpty() }, if (e == null) t("installed_manually") else null).joinToString("  ·  ")
    val selected = app.selectedRow === r
    ListRow({ LetterIcon(r.title ?: "?") }, title, sub, selected, onClick = { app.selectedRow = r }) {
        val busy = e?.let { app.busy[it.key] }
        when {
            busy != null -> Pill(busy, Palette.rune)
            r.missing -> Pill(t("pill_folders_missing"), Palette.warn)
            e != null && e.updateAvailable -> Button(t("action_update"), primary = true) { app.update(e) }
            else -> Pill(r.version?.takeIf { it.isNotEmpty() } ?: "—")
        }
    }
}

@Composable
private fun RowDetail(app: AppState, r: Model.Installed?) {
    if (r == null) { DetailHint(t("detail_pick_addon"), t("detail_hint_library")); return }
    val e = r.entry
    DetailTitle(r.title ?: "?", r.author ?: "")
    val versions = buildString {
        append(r.version?.takeIf { it.isNotEmpty() } ?: "—")
        if (e?.updateAvailable == true) append("  →  ").append(e.latest)
    }
    Pill(versions, if (e?.updateAvailable == true) Palette.gold else Palette.text2)
    e?.checkError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Palette.warn, fontSize = 13.sp) }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val busy = e?.let { app.busy[it.key] }
        if (busy != null) Pill(busy, Palette.rune)
        else {
            if (r.withModules().any { it.entry?.updateAvailable == true }) Button(t("action_update"), primary = true, icon = "ic_upgrade") { app.updateRow(r) }
            else if (e != null) Button(t("action_reinstall"), icon = "ic_refresh") { app.update(e) }
            else r.catalogMatch?.let { c -> Button(t("action_install"), icon = "ic_download") { app.install(c) } }
            Button(t("action_remove"), icon = "ic_delete") { app.confirmRemove = r }
        }
    }
    e?.pageUrl?.let { url -> Spacer(Modifier.height(10.dp)); Button(t("action_page"), icon = "ic_open") { openUrl(url) } }
    Spacer(Modifier.height(20.dp))
    val folders = Library.removeFolders(r)
    if (folders.isNotEmpty()) {
        SectionTitle(q("folders_count", folders.size))
        Text(folders.joinToString("\n"), color = Palette.text2, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
    }
    val notes = e?.changelog?.takeIf { it.isNotBlank() }
    if (notes != null) {
        SectionTitle(if (e.latest != null) t("detail_whats_new_in", e.latest) else t("detail_whats_new"))
        Text(markdown(notes), color = Palette.text2, fontSize = 13.sp)
    } else if (!r.notes.isNullOrBlank()) {
        SectionTitle(t("detail_description"))
        Text(r.notes, color = Palette.text2, fontSize = 13.sp)
    }
}

@Composable
private fun GameMissing(app: AppState) {
    Empty("ic_folder", t("game_where_title"), t("game_where_text")) {
        Button(t("game_auto_detect"), icon = "ic_search") { app.detect() }
        Button(t("game_pick_folder"), primary = true, icon = "ic_folder") { pickFolder()?.let(app::setGame) }
    }
}

// ── Browse ──

@Composable
fun BrowseScreen(app: AppState) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<Model.Item>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(300)
        error = null
        try {
            val page = withContext(Dispatchers.IO) { Search.page(query, null, app.store.verifiedOnly(), 0, 200) }
            items = page.items
            error = page.warning
        } catch (e: Exception) {
            error = Texts.friendly(e)
        }
    }
    app.revision
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Header(t("browse_title"))
            SearchField(query, t("browse_hint_github")) { query = it }
            if (!app.gameReady) Text(t("browse_no_game"), color = Palette.warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
            if (error != null) Text(error!!, color = Palette.warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.key() }) { item ->
                    ListRow({ LetterIcon(item.name ?: item.id) }, item.name ?: item.id, item.summary.ifEmpty { item.author },
                        app.selectedItem?.key() == item.key(), onClick = { app.selectedItem = item }) {
                        ItemAction(app, item)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        DetailPane { ItemDetail(app, app.selectedItem) }
    }
}

@Composable
private fun ItemAction(app: AppState, item: Model.Item) {
    val busy = app.busy[item.key()]
    val entry = app.entryOf(item)
    when {
        busy != null -> Pill(busy, Palette.rune)
        entry != null && entry.updateAvailable -> Button(t("action_update"), primary = true) { app.update(entry) }
        entry != null -> Pill(t("pill_installed"), Palette.rune)
        else -> Button(t("action_install"), primary = true, enabled = app.gameReady) { app.install(item) }
    }
}

@Composable
private fun ItemDetail(app: AppState, item: Model.Item?) {
    if (item == null) { DetailHint(t("detail_pick_addon"), t("detail_hint_browse")); return }
    DetailTitle(item.name ?: item.id, item.author)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item.version?.takeIf { it.isNotEmpty() }?.let { Pill(it) }
        if (item.foreverVerified) Pill(t("pill_forever"), Palette.rune)
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val entry = app.entryOf(item)
        val busy = app.busy[item.key()]
        when {
            busy != null -> Pill(busy, Palette.rune)
            entry == null -> Button(t("action_install"), primary = true, icon = "ic_download", enabled = app.gameReady) { app.install(item) }
            entry.updateAvailable -> Button(t("action_update"), primary = true, icon = "ic_upgrade") { app.update(entry) }
            else -> Button(t("action_reinstall"), icon = "ic_refresh") { app.install(item) }
        }
        item.pageUrl?.let { url -> Button(t("action_page"), icon = "ic_open") { openUrl(url) } }
    }
    item.note?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Palette.warn, fontSize = 13.sp) }
    if (!app.gameReady) { Spacer(Modifier.height(10.dp)); Text(t("browse_no_game"), color = Palette.warn, fontSize = 13.sp) }
    Spacer(Modifier.height(20.dp))
    val about = item.description.ifEmpty { item.summary }
    if (about.isNotBlank()) {
        SectionTitle(t("detail_description"))
        Text(markdown(about), color = Palette.text2, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
    }
    if (item.changelog.isNotBlank()) {
        SectionTitle(t("detail_whats_new"))
        Text(markdown(item.changelog), color = Palette.text2, fontSize = 13.sp)
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.padding(horizontal = 28.dp).padding(bottom = 12.dp).fillMaxWidth().clip(shape)
        .background(Palette.surface).border(1.dp, Palette.stroke, shape).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(drawable("ic_search"), null, Modifier.size(18.dp), tint = Palette.text3)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Palette.text3, fontSize = 14.sp)
            BasicTextField(value, onChange, singleLine = true, cursorBrush = SolidColor(Palette.gold),
                textStyle = TextStyle(color = Palette.text, fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── Settings ──

@Composable
fun SettingsScreen(app: AppState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(t("settings_title"))
        Column(Modifier.padding(horizontal = 28.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Text(t("settings_game_folder"), color = Palette.text, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                val root = app.gameRoot
                Text(root ?: t("settings_pick_game"), color = if (root == null || app.gameReady) Palette.text2 else Palette.warn, fontSize = 13.sp)
                if (root != null && !app.gameReady) Text(t("settings_game_missing"), color = Palette.warn, fontSize = 13.sp)
                app.store.addonsDir()?.let {
                    Text(t(if (it.isDirectory) "settings_addons_to" else "settings_addons_to_new", it.absolutePath), color = Palette.text3, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(t("game_auto_detect"), icon = "ic_search") { app.detect() }
                    Button(t("game_pick_folder"), primary = true, icon = "ic_folder") { pickFolder()?.let(app::setGame) }
                }
                if (app.found.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Text(t("game_multiple_found"), color = Palette.text, fontSize = 14.sp)
                    for (f in app.found) Text(f.absolutePath, color = Palette.rune, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { app.setGame(f) }.padding(vertical = 6.dp))
                }
            }
            Card {
                Text(t("settings_about"), color = Palette.text, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(t("settings_version", t("app_name"), APP_VERSION), color = Palette.text2, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(t("settings_about_text"), color = Palette.text3, fontSize = 13.sp)
            }
        }
    }
}

// ── shared ──

/** The detail column on the right, as in the design (372 dp). */
@Composable
private fun DetailPane(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.stroke))
    Column(Modifier.width(372.dp).fillMaxHeight().background(Palette.rail).verticalScroll(rememberScrollState()).padding(24.dp), content = content)
}

@Composable
private fun DetailTitle(title: String, author: String) {
    Text(title, color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    if (author.isNotEmpty()) Text(author, color = Palette.text3, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DetailHint(title: String, text: String) {
    Spacer(Modifier.height(40.dp))
    Text(title, color = Palette.text2, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    Text(text, color = Palette.text3, fontSize = 13.sp)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Palette.surface).border(1.dp, Palette.stroke, shape).padding(16.dp), content = content)
}

/** The system folder picker; null when the player cancels. */
fun pickFolder(): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = t("game_where_title")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

fun openUrl(url: String) {
    try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) } catch (e: Exception) { }
}
