package app.wowforever.addons

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One list line: a Library row, or a part of an open package (the core is the package's own row). */
private class Line(val row: Model.Installed, val part: Boolean)

/** Column widths of the Library table; narrow windows drop the version and source columns. */
private class Cols(val version: Dp, val source: Dp, val status: Dp)

@Composable
fun LibraryScreen(app: AppState) {
    app.revision
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val sub = listOfNotNull(
                q("addons_count", app.rows.size),
                if (app.managed > 0) t("installed_managed", app.managed) else null,
                if (app.checking) t("installed_checking") else null,
            ).joinToString("  ·  ")
            Header(t("library_title"), if (app.scanned && app.gameReady) sub else null) {
                if (app.gameReady) {
                    Button(t("action_from_zip"), Kind.GHOST, icon = "ic_zip") { pickZip()?.let(app::installZip) }
                    if (app.managed > 0) Button(if (app.checking) t("action_checking") else t("action_check"), icon = "ic_refresh", enabled = !app.checking) { app.checkUpdates(true) }
                }
            }
            when {
                !app.gameReady -> GameMissing(app)
                !app.scanned -> Unit
                app.rows.isEmpty() -> Empty("ic_extension", t("installed_empty_title"), t("installed_empty_text")) {
                    Button(t("action_open_catalog"), Kind.PRIMARY, icon = "ic_apps") { app.tab = Tab.BROWSE }
                    Button(t("action_install_zip"), icon = "ic_zip") { pickZip()?.let(app::installZip) }
                }
                else -> LibraryList(app)
            }
        }
        if (app.gameReady && app.rows.isNotEmpty()) DetailPane { RowDetail(app, app.selectedRow) }
    }
}

@Composable
private fun LibraryList(app: AppState) {
    val names = app.rows.filter(::anyUpdate).map { it.title }
    if (names.isNotEmpty()) UpdateBar(app, names)
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(app.libraryQuery, t("library_filter_hint"), Modifier.weight(1f)) { app.libraryQuery = it }
        Spacer(Modifier.width(10.dp))
        Segmented(listOf(false, true), app.libraryUpdatesOnly, { if (it) t("filter_updates") else t("filter_all") }) { app.libraryUpdatesOnly = it }
    }
    val needle = app.libraryQuery.trim().lowercase()
    val sorted = app.rows
        .filter { r -> needle.isEmpty() || r.withModules().any { (it.title ?: "").lowercase().contains(needle) } }
        .filter { !app.libraryUpdatesOnly || anyUpdate(it) }
        .sortedWith(compareBy<Model.Installed> { if (anyUpdate(it)) 0 else 1 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title ?: "" })
    val lines = buildList {
        for (r in sorted) {
            add(Line(r, false))
            if (r.isPackage && app.expanded[rowId(r)] == true) for (m in r.withModules()) add(Line(m, true))
        }
    }
    if (lines.isEmpty()) { Empty("ic_search", t("nothing_found"), t("favorites_no_match")); return }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        val cols = when {
            maxWidth >= 640.dp -> Cols(150.dp, 92.dp, 128.dp)
            maxWidth >= 500.dp -> Cols(130.dp, 0.dp, 116.dp)
            else -> Cols(0.dp, 0.dp, 110.dp)
        }
        Column {
            TableHead(cols)
            val state = rememberLazyListState()
            Box {
                LazyColumn(Modifier.fillMaxSize().padding(end = 8.dp), state) {
                    items(lines, key = { (if (it.part) "p:" else "r:") + rowId(it.row) }) { l ->
                        when {
                            l.part -> PartRow(app, l.row, cols)
                            l.row.isPackage -> PackageRow(app, l.row, cols)
                            else -> AddonRow(app, l.row, cols)
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight(), style = scrollbarStyle())
            }
        }
    }
}

/** Gold strip above the list: "3 updates available: A, B, C" and an "Update all" button. */
@Composable
private fun UpdateBar(app: AppState, names: List<String>) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp).fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(Palette.gold.copy(alpha = 0.18f), Palette.gold.copy(alpha = 0.04f))))
            .border(1.dp, Palette.gold.copy(alpha = 0.3f), shape).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(drawable("ic_upgrade"), null, Modifier.size(18.dp), tint = Palette.gold)
        Spacer(Modifier.width(10.dp))
        val head = q("updates_available", names.size)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.gold, fontWeight = FontWeight.SemiBold)) { append(head) }
            append(":  ")
            append(names.joinToString(", "))
        }, color = Palette.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Button(t("action_update_all_plain") + " · " + app.updates, Kind.PRIMARY, icon = "ic_download") { app.updateAll() }
    }
}

@Composable
private fun TableHead(cols: Cols) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(54.dp))
        Label(t("col_addon"), Modifier.weight(1f))
        if (cols.version > 0.dp) Label(t("col_version"), Modifier.width(cols.version))
        if (cols.source > 0.dp) Label(t("col_source"), Modifier.width(cols.source))
        Box(Modifier.width(cols.status), contentAlignment = Alignment.CenterEnd) { Label(t("col_status")) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.stroke))
}

/** The frame of a table row: hover and selection light, a hairline under it. */
@Composable
private fun TableRow(selected: Boolean, indent: Dp = 0.dp, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(9.dp)
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape)
                .background(when { selected -> Palette.surface2; hovered -> Palette.surface; else -> Color.Transparent })
                .border(1.dp, if (selected) Palette.stroke else Color.Transparent, shape)
                .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
                .padding(start = 12.dp + indent, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically, content = content,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.stroke.copy(alpha = 0.5f)))
    }
}

@Composable
private fun NameCell(title: String, sub: String, subColor: Color = Palette.text3, trailing: @Composable () -> Unit = {}) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            trailing()
        }
        Text(sub, color = subColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "1.14.3 » 1.15.0" in mono, the new version in gold. */
@Composable
fun VersionText(from: String?, to: String?) {
    Text(buildAnnotatedString {
        append(clip(from?.takeIf { it.isNotEmpty() } ?: "—", 14))
        if (to != null) withStyle(SpanStyle(color = Palette.gold)) { append("  »  " + clip(to, 14)) }
    }, color = Palette.text2, fontFamily = Fonts.mono, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

fun clip(v: String?, max: Int): String = if (v == null) "" else if (v.length > max) v.take(max - 1) + "…" else v

@Composable
private fun SourceTag(r: Model.Installed) {
    val src = r.entry?.source ?: r.catalogMatch?.source
    when {
        src == null -> Tag(t("pill_manual"))
        src == Model.SRC_WOWI -> Tag("WoWI", Palette.blue, Palette.blueSoft)
        src == Model.SRC_ZIP -> Tag("ZIP")
        r.entry == null -> Tag(t("pill_in_catalog"), Palette.blue, Palette.blueSoft)
        else -> Tag("GitHub")
    }
}

/** The state of one addon at the end of its row. */
@Composable
private fun RowStatus(app: AppState, r: Model.Installed) {
    val e = r.entry
    val busy = busyKey(r)?.let { app.busy[it] }
    val ended = e != null && e.source == Model.SRC_GITHUB && Index.item(e.id)?.isDiscontinued == true
    when {
        busy != null -> BusyBar(busy)
        r.missing -> StateText(t("pill_folders_missing"), Palette.danger, "ic_warning")
        e != null && e.updateAvailable -> Button(t("action_update"), Kind.PRIMARY, small = true) { app.update(e) }
        ended -> StateText(t("tag_discontinued"), Palette.warn, "ic_warning")
        e != null && e.checkError != null -> StateText(t("pill_unchecked"), Palette.text3, null)
        e != null && e.latest != null -> StateText(t("pill_up_to_date"), Palette.rune, "ic_check")
        e == null && r.catalogMatch != null -> Button(t("action_track_catalog"), small = true) { app.install(r.catalogMatch) }
        e == null -> StateText(t("pill_manual"), Palette.text3, null)
        else -> Unit
    }
}

@Composable
private fun AddonRow(app: AppState, r: Model.Installed, cols: Cols) {
    val e = r.entry
    val selected = app.selectedRow?.let { rowId(it) == rowId(r) } == true
    TableRow(selected, onClick = { app.selectedRow = r }) {
        AddonIcon(e?.iconUrl ?: r.catalogMatch?.iconUrl, r.title, 36.dp)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f)) {
            val sub = listOfNotNull(r.author?.takeIf { it.isNotEmpty() }, if (r.folders.size > 1) q("folders_count", r.folders.size) else null)
                .joinToString(" · ").ifEmpty { sourceName(e?.source) }
            NameCell(r.title ?: "?", sub) {
                if (!r.compatible && !r.missing) {
                    Spacer(Modifier.width(6.dp))
                    Icon(drawable("ic_warning"), t("pill_not_forever"), Modifier.size(14.dp), tint = Palette.warn)
                }
            }
        }
        val update = e?.updateAvailable == true && !r.missing
        if (cols.version > 0.dp) Box(Modifier.width(cols.version)) { VersionText(e?.version?.takeIf { it.isNotEmpty() } ?: r.version, if (update) e?.latest else null) }
        if (cols.source > 0.dp) Box(Modifier.width(cols.source)) { SourceTag(r) }
        Box(Modifier.width(cols.status), contentAlignment = Alignment.CenterEnd) { RowStatus(app, r) }
    }
}

/** A package: one line for the core and its modules; a click opens the list of parts and shows the package. */
@Composable
private fun PackageRow(app: AppState, r: Model.Installed, cols: Cols) {
    val id = rowId(r)
    val open = app.expanded[id] == true
    val selected = app.selectedRow?.let { rowId(it) == id } == true
    val parts = r.withModules()
    val updates = parts.count { it.entry?.updateAvailable == true }
    val busy = parts.firstNotNullOfOrNull { m -> busyKey(m)?.let { app.busy[it] } }
    TableRow(selected, onClick = { app.expanded[id] = !open; app.selectedRow = r }) {
        AddonIcon(r.entry?.iconUrl ?: r.catalogMatch?.iconUrl, r.title, 36.dp)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f)) {
            val count = q("modules_count", r.modules.size)
            val sub = if (updates > 0 && busy == null) q("updates_available", updates) + " · " + count
                else listOfNotNull(r.author?.takeIf { it.isNotEmpty() }, count).joinToString(" · ")
            NameCell(r.title ?: "?", sub, if (updates > 0 && busy == null) Palette.gold else Palette.text3) {
                Spacer(Modifier.width(6.dp))
                Icon(drawable("ic_chevron"), t(if (open) "action_hide_modules" else "action_show_modules"),
                    Modifier.size(18.dp).rotate(if (open) 270f else 90f), tint = Palette.text3)
            }
        }
        if (cols.version > 0.dp) Box(Modifier.width(cols.version)) { VersionText(r.entry?.version?.takeIf { it.isNotEmpty() } ?: r.version, null) }
        if (cols.source > 0.dp) Box(Modifier.width(cols.source)) { SourceTag(r) }
        Box(Modifier.width(cols.status), contentAlignment = Alignment.CenterEnd) {
            when {
                busy != null -> BusyBar(busy)
                parts.any { it.missing } -> StateText(t("pill_folders_missing"), Palette.danger, "ic_warning")
                updates > 0 -> Button(t("action_update"), Kind.PRIMARY, small = true) { app.updateRow(r) }
                parts.any { it.entry?.checkError != null } -> StateText(t("pill_unchecked"), Palette.text3, null)
                r.entry?.latest != null -> StateText(t("pill_up_to_date"), Palette.rune, "ic_check")
                r.entry == null -> StateText(t("pill_manual"), Palette.text3, null)
            }
        }
    }
}

/** A part of an open package, indented under it: the core first, then each module by its short name. */
@Composable
private fun PartRow(app: AppState, m: Model.Installed, cols: Cols) {
    val core = m.parent == null
    val pkg = m.parent ?: m
    val selected = !core && app.selectedRow?.let { rowId(it) == rowId(m) } == true
    TableRow(selected, indent = 24.dp, onClick = { app.selectedRow = m }) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { AddonIcon(m.entry?.iconUrl, if (core) pkg.title else Packages.moduleName(m.title, pkg.title), 26.dp) }
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f)) {
            NameCell(if (core) t("module_core") else Packages.moduleName(m.title, pkg.title), m.author?.takeIf { it.isNotEmpty() } ?: sourceName(m.entry?.source))
        }
        val e = m.entry
        if (cols.version > 0.dp) Box(Modifier.width(cols.version)) { VersionText(e?.version?.takeIf { it.isNotEmpty() } ?: m.version, if (e?.updateAvailable == true) e.latest else null) }
        if (cols.source > 0.dp) Spacer(Modifier.width(cols.source))
        Box(Modifier.width(cols.status), contentAlignment = Alignment.CenterEnd) { RowStatus(app, m) }
    }
}

@Composable
fun GameMissing(app: AppState) {
    Empty("ic_folder", t("game_where_title"), t("game_where_text")) {
        Button(t("game_auto_detect"), icon = "ic_search") { app.detect() }
        Button(t("game_pick_folder"), Kind.PRIMARY, icon = "ic_folder") { pickFolder()?.let(app::setGame) }
    }
}
