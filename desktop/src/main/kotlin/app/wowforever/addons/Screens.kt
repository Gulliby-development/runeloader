package app.wowforever.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

// The three sections. M1 shows the data; installing and updating come with M2 (#95).

@Composable
fun LibraryScreen(app: AppState) {
    Column(Modifier.fillMaxSize()) {
        Header(t("library_title"), if (app.entries.isEmpty()) null else t("installed_managed", app.entries.size))
        when {
            !app.gameReady -> GameMissing(app)
            app.entries.isEmpty() -> Empty("ic_extension", t("installed_empty_title"), t("installed_empty_text")) {
                Button(t("action_open_catalog"), primary = true, icon = "ic_search") { app.tab = Tab.BROWSE }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(app.entries, key = { it.key }) { e ->
                    ListRow({ LetterIcon(e.name ?: e.key) }, e.name ?: e.key, e.author ?: "", e.version)
                }
            }
        }
    }
}

@Composable
private fun GameMissing(app: AppState) {
    Empty("ic_folder", t("game_where_title"), t("game_where_text")) {
        Button(t("game_auto_detect"), icon = "ic_search") { app.detect() }
        Button(t("game_pick_folder"), primary = true, icon = "ic_folder") { pickFolder()?.let(app::setGame) }
    }
}

@Composable
fun BrowseScreen(app: AppState) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<Model.Item>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(300)
        error = null
        try {
            val page = withContext(Dispatchers.IO) { Search.page(query, null, app.store.verifiedOnly(), 0, 100) }
            items = page.items
            error = page.warning
        } catch (e: Exception) {
            error = Texts.friendly(e)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header(t("browse_title"))
        SearchField(query, t("browse_hint_github")) { query = it }
        if (error != null) Text(error!!, color = Palette.warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.source + ":" + it.id }) { it ->
                ListRow({ LetterIcon(it.name ?: it.id) }, it.name ?: it.id, it.summary.ifEmpty { it.author }, it.version)
            }
        }
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

@Composable
fun SettingsScreen(app: AppState) {
    Column(Modifier.fillMaxSize()) {
        Header(t("settings_title"))
        Column(Modifier.padding(horizontal = 28.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Text(t("settings_game_folder"), color = Palette.text, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                val root = app.gameRoot
                Text(root ?: t("settings_pick_game"), color = if (root == null || app.gameReady) Palette.text2 else Palette.warn, fontSize = 13.sp)
                if (root != null && !app.gameReady) Text(t("settings_game_missing"), color = Palette.warn, fontSize = 13.sp)
                app.store.addonsDir()?.let { Text(t("settings_addons_to", it.absolutePath), color = Palette.text3, fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(t("game_auto_detect"), icon = "ic_search") { app.detect() }
                    Button(t("game_pick_folder"), primary = true, icon = "ic_folder") { pickFolder()?.let(app::setGame) }
                }
                app.message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Palette.rune, fontSize = 13.sp) }
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
