package app.wowforever.addons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The addon detail column, with everything the Android DetailPanel shows.

@Composable
fun DetailHint(title: String, text: String, icon: String) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Palette.surface2)
            .border(1.dp, Palette.stroke, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(drawable(icon), null, Modifier.size(30.dp), tint = Palette.text3)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Palette.text2, fontFamily = Fonts.display, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Palette.text3, fontSize = 12.5.sp, textAlign = TextAlign.Center)
    }
}

/** Logo with a soft glow, name, author and source, the star at the right, then the status pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(icon: String?, name: String, byline: String, fav: Model.Item?, app: AppState, pills: @Composable FlowRowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = Palette.rune.copy(alpha = 0.4f), spotColor = Palette.rune.copy(alpha = 0.5f))) {
            AddonIcon(icon, name, 56.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Palette.text, fontFamily = Fonts.display, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (byline.isNotEmpty()) Text(byline, color = Palette.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (fav != null) {
            val on = app.isFavorite(fav)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Palette.goldSoft else Palette.surface2)
                .border(1.dp, if (on) Palette.gold.copy(alpha = 0.5f) else Palette.stroke, RoundedCornerShape(10.dp))
                .pointerHoverIcon(PointerIcon.Hand).clickable { app.toggleFavorite(fav) }, contentAlignment = Alignment.Center) {
                Icon(drawable(if (on) "ic_star" else "ic_star_outline"), t(if (on) "favorite_remove" else "favorite_add"),
                    Modifier.size(18.dp), tint = if (on) Palette.gold else Palette.text2)
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = pills)
}

private fun byline(author: String?, source: String): String =
    if (author.isNullOrEmpty()) source else "$author  ·  $source"

/** A box of the detail column with a mono head. */
@Composable
private fun Section(title: String?, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glass(10.dp, highlight = if (accent) Palette.gold.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
        stroke = if (accent) Palette.gold.copy(alpha = 0.45f) else Palette.stroke).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) Label(title, color = if (accent) Palette.gold else Palette.text3)
        content()
    }
}

/** Key and value lines; values in mono. */
@Composable
private fun InfoRow(label: String, value: String?, mono: Boolean = true) {
    if (value.isNullOrEmpty()) return
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = Palette.text3, fontSize = 12.5.sp, modifier = Modifier.width(110.dp))
        Text(value, color = Palette.text2, fontFamily = if (mono) Fonts.mono else Fonts.body, fontSize = if (mono) 11.5.sp else 12.5.sp,
            modifier = Modifier.weight(1f))
    }
}

/** Long text shown short first, with a toggle. */
@Composable
private fun Expandable(text: AnnotatedString, color: Color = Palette.text2) {
    var open by remember(text) { mutableStateOf(false) }
    val long = text.length > 420
    val shown = if (!long || open) text else {
        val s = text.text
        val nl = s.lastIndexOf('\n', 420)
        val sp = s.lastIndexOf(' ', 420)
        var end = if (nl > 210) nl else if (sp > 210) sp else 420
        while (end > 0 && s[end - 1].isWhitespace()) end--
        text.subSequence(0, end) + AnnotatedString("…")
    }
    Text(shown, color = color, fontSize = 12.5.sp, lineHeight = 18.sp)
    if (long) Text(t(if (open) "show_less" else "show_more"), color = Palette.rune, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { open = !open })
}

@Composable
private fun Changelog(version: String?, text: String?, isUpdate: Boolean) {
    if (text.isNullOrBlank()) return
    Section(if (version.isNullOrEmpty()) t("detail_whats_new") else t("detail_whats_new_in", clip(version, 22)), accent = isUpdate) {
        Expandable(markdown(text))
    }
}

@Composable
private fun Description(text: String?) {
    if (text.isNullOrBlank()) return
    Section(t("detail_description")) { Expandable(markdown(text)) }
}

@Composable
private fun Screenshots(urls: List<String>) {
    if (urls.isEmpty()) return
    Label(t("detail_screenshots"))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(urls) { u ->
            val img = rememberUrlImage(u)
            Box(Modifier.size(196.dp, 110.dp).clip(RoundedCornerShape(10.dp)).background(Palette.surface2)
                .border(1.dp, Palette.stroke, RoundedCornerShape(10.dp)).pointerHoverIcon(PointerIcon.Hand).clickable { openUrl(u) }) {
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

/** Recent releases from the index; each row opens its GitHub release page. */
@Composable
private fun Releases(rels: List<Model.Release>, installed: String?, repo: String?) {
    val list = rels.size >= 2
    if (!list && repo == null) return
    Section(t("detail_versions")) {
        if (list) for (r in rels) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).let { m -> r.changelogUrl?.let { u -> m.pointerHoverIcon(PointerIcon.Hand).clickable { openUrl(u) } } ?: m }
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(clip(r.version, 22), color = Palette.text, fontFamily = Fonts.mono, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                if (installed != null && UpdatePolicy.sameVersion(r.version, installed)) { Pill(t("pill_installed"), Palette.rune, Palette.runeSoft); Spacer(Modifier.width(6.dp)) }
                if (r.prerelease) { Pill(t("pill_beta"), Palette.warn, Palette.warnSoft); Spacer(Modifier.width(6.dp)) }
                if (r.released > 0) Text(formatDate(r.released), color = Palette.text3, fontSize = 11.5.sp)
            }
        }
        if (repo != null) Row(Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { openUrl("https://github.com/$repo/releases") }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(t("detail_all_releases"), color = Palette.rune, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Icon(drawable("ic_open"), null, Modifier.size(14.dp), tint = Palette.rune)
        }
    }
}

/**
 * The index says the author stopped this addon: say so, quote the author, and offer the replacement.
 */
@Composable
private fun Ended(app: AppState, ix: Model.Item?, lead: Boolean) {
    if (ix == null || !ix.isDiscontinued) return
    val text = buildString {
        append(t(if (ix.discontinued == "archived") "note_discontinued_archived" else "note_discontinued"))
        if (ix.discontinuedNote.isNotEmpty()) append("\n").append(t("note_discontinued_author", ix.discontinuedNote))
    }
    Note(text, Palette.danger, Palette.dangerSoft, "ic_warning")
    val next = ix.successor?.let { Index.item(it) } ?: return
    Button(t("action_use_successor", next.name), if (lead) Kind.PRIMARY else Kind.SECONDARY, icon = "ic_link") { app.showItem(next) }
}

/**
 * The modules of a package (core owner/repo): each one installed or not, with a button to add or remove
 * just that module. Installed modules the index no longer lists show too.
 */
@Composable
private fun Modules(app: AppState, repo: String, pkg: Model.Installed?) {
    if (Modules.moduleOf(repo) != null) return
    val mods = Index.modulesOf(repo)
    val installed = pkg?.modules ?: emptyList()
    // An installed module matches its index entry by the managed key or, when installed by hand, by its catalog match.
    fun keyOf(m: Model.Installed) = m.entry?.key ?: m.catalogMatch?.key()
    fun installedOf(it: Model.Item) = installed.firstOrNull { m -> keyOf(m)?.equals(it.key(), ignoreCase = true) == true }
    val extra = installed.filter { m -> mods.none { it.key().equals(keyOf(m), ignoreCase = true) } }
    if (mods.isEmpty() && extra.isEmpty()) return
    val coreName = pkg?.title ?: Index.item(repo)?.name ?: ""
    val total = mods.size + extra.size
    Section(t("modules_title") + "  ·  " + (if (pkg != null) "${installed.size} / $total" else "$total")) {
        Text(t("modules_hint"), color = Palette.text3, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        for (it in mods) {
            val m = installedOf(it)
            val me = app.store.entry(it.key())
            val newer = m != null && Library.needsUpdate(m)
            ModuleLine(app, Packages.moduleName(it.name, coreName), it.iconUrl, m?.version ?: me?.version, it.key(),
                remove = when { me != null -> ({ app.removeEntry(me) }); m != null -> ({ app.confirmRemove = m }); else -> null },
                install = when {
                    m == null && me == null -> ({ app.install(it) })
                    newer && me != null -> ({ app.update(me) })
                    newer -> ({ app.install(it, alone = true) })
                    else -> null
                }, update = newer)
        }
        for (m in extra) ModuleLine(app, Packages.moduleName(m.title, coreName), m.entry?.iconUrl, m.version, m.entry?.key,
            remove = { app.confirmRemove = m }, install = null)
    }
}

@Composable
private fun ModuleLine(app: AppState, name: String, icon: String?, version: String?, key: String?, remove: (() -> Unit)?, install: (() -> Unit)?,
                       update: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Palette.surface2).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AddonIcon(icon, name, 26.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!version.isNullOrEmpty()) Text(version, color = Palette.text3, fontFamily = Fonts.mono, fontSize = 11.sp, maxLines = 1)
        }
        val busy = key?.let { app.busy[it] }
        when {
            busy != null -> Pill(t("pill_installing"), Palette.gold, Palette.goldSoft)
            install != null && update -> Button(t("action_update"), Kind.PRIMARY, small = true, onClick = install)
            install != null -> Button(null, Kind.SECONDARY, icon = "ic_download", small = true, onClick = install)
            remove != null -> Button(null, Kind.DANGER, icon = "ic_delete", small = true, onClick = remove)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

// ── an installed row ──

@Composable
fun RowDetail(app: AppState, r: Model.Installed?) {
    if (r == null) { DetailHint(t("detail_pick_addon"), t("detail_hint_library"), "ic_extension"); return }
    app.revision
    val e = r.entry
    val managed = e != null && e.source != Model.SRC_ZIP
    val busyKey = busyKey(r)
    val busy = busyKey?.let { app.busy[it] }
    val ix = if (e != null && e.source == Model.SRC_GITHUB) Index.item(e.id) else null
    val fav = when {
        managed -> ix ?: Sources.itemFor(e)
        r.catalogMatch != null -> r.catalogMatch
        else -> null
    }
    Hero(e?.iconUrl ?: r.catalogMatch?.iconUrl, r.title ?: "?", byline(r.author, if (e != null) sourceName(e.source) else t("pill_manual")), fav, app) {
        when {
            busy != null -> Pill(t("pill_installing"), Palette.gold, Palette.goldSoft)
            r.missing -> Pill(t("pill_folders_missing"), Palette.danger, Palette.dangerSoft)
            e != null && e.updateAvailable -> Pill(t("action_update_to", clip(e.latest, 22)), Palette.gold, Palette.goldSoft, "ic_upgrade")
            Library.untrackedUpdate(r) != null -> Pill(t("action_update_to", clip(Library.untrackedUpdate(r), 22)), Palette.gold, Palette.goldSoft, "ic_upgrade")
            r.modules.any { Library.needsUpdate(it) } -> Pill(q("updates_available", r.modules.count { Library.needsUpdate(it) }), Palette.gold, Palette.goldSoft, "ic_upgrade")
            e != null && e.checkError != null -> Pill(t("pill_unchecked"))
            e != null && e.latest != null -> Pill(t("pill_up_to_date"), Palette.rune, Palette.runeSoft, "ic_check")
            e == null && r.catalogMatch != null -> Pill(t("pill_in_catalog"), Palette.blue, Palette.blueSoft)
            e == null -> Pill(t("pill_manual"))
        }
        if (!r.missing) {
            if (r.compatible) Pill(t("pill_forever"), Palette.rune, Palette.runeSoft)
            else Pill(t("pill_not_forever"), Palette.warn, Palette.warnSoft)
        }
        if (ix?.isDiscontinued == true) Pill(t("tag_discontinued"), Palette.danger, Palette.dangerSoft)
        if (r.isPackage) Pill(q("modules_count", r.modules.size), Palette.blue, Palette.blueSoft)
    }

    Actions {
        when {
            busy != null -> BusyBar(busy)
            managed && r.withModules().any { Library.needsUpdate(it) } ->
                Button(if (e.updateAvailable) t("action_update_to", clip(e.latest, 22)) else t("action_update"), Kind.PRIMARY, icon = "ic_upgrade") { app.updateRow(r) }
            managed && r.missing -> Button(t("action_install_again"), Kind.PRIMARY, icon = "ic_download") { app.update(e) }
            managed -> Button(t("action_check_update"), icon = "ic_refresh") { app.checkOne(e) }
            e == null && r.withModules().any { Library.needsUpdate(it) } ->
                Button(Library.untrackedUpdate(r)?.let { t("action_update_to", clip(it, 22)) } ?: t("action_update"), Kind.PRIMARY, icon = "ic_upgrade") { app.updateRow(r) }
            e == null && r.catalogMatch != null -> Button(t("action_track_catalog"), Kind.PRIMARY, icon = "ic_link") { app.install(r.catalogMatch, alone = true) }
            e == null && r.main()?.wowiId != null -> Button(t("action_track_wowi"), Kind.PRIMARY, icon = "ic_link") { app.adoptWowi(r.main().wowiId, r) }
        }
        // a clean copy of the installed addon, e.g. one installed by an older version with a bug
        if (busy == null && managed && !e.updateAvailable && !r.missing) Button(t("action_reinstall"), icon = "ic_download") { app.update(e) }
        val page = e?.pageUrl ?: r.catalogMatch?.pageUrl ?: r.main()?.website
        if (page != null) Button(t("action_page"), Kind.GHOST, icon = "ic_open") { openUrl(page) }
        val dir = r.main()?.folder?.let { f -> app.store.addonsDir()?.let { java.io.File(it, f) } }
        if (dir != null && !r.missing) Button(t("action_open_folder"), Kind.GHOST, icon = "ic_folder") { openFolder(dir) }
        Button(t(if (r.isPackage) "action_remove_all" else "action_remove"), Kind.DANGER, icon = "ic_delete") { app.confirmRemove = r }
    }

    if (e != null && e.source == Model.SRC_GITHUB) Ended(app, ix, false)
    if (!r.compatible && !r.missing) Note(t("note_not_forever"), Palette.warn, Palette.warnSoft, "ic_warning")
    e?.checkError?.let { Note(t("note_check_error", it), Palette.text2, Palette.surface2) }
    val cat = if (e != null) Catalog.byKey(e.key) else r.catalogMatch
    cat?.note?.let { Note(it, Palette.warn, Palette.warnSoft) }
    if (e == null && r.catalogMatch != null) Note(t("note_manual_in_catalog"), Palette.blue, Palette.blueSoft)

    if (e != null && e.source == Model.SRC_GITHUB && r.parent == null) Modules(app, e.id, r)

    val meta = if (e != null) indexMeta(e.source, e.id, null) else r.catalogMatch?.let { indexMeta(it.source, it.id, it) } ?: Model.Item()
    val news = e?.changelog?.takeIf { it.isNotEmpty() } ?: meta.changelog
    val newsVer = e?.latest ?: meta.releases.firstOrNull()?.version
    Changelog(newsVer, news, e?.updateAvailable == true)
    if (!r.notes.isNullOrBlank() && meta.description.isEmpty()) Section(null) { Text(r.notes, color = Palette.text2, fontSize = 12.5.sp) }
    Screenshots(meta.screenshots)
    Description(meta.description)

    Section(t("pill_installed")) {
        InfoRow(t("info_version"), r.version)
        if (e?.latest != null) InfoRow(t("info_latest"), e.latest)
        if (e != null && e.installedAt > 0) InfoRow(t("info_installed_at"), formatDate(e.installedAt), mono = false)
        InfoRow(t("info_interface"), r.main()?.interfaceVersion)
        val fs = r.folders.map { it.folder }.ifEmpty { e?.folders ?: emptyList() }
        InfoRow(t("info_folders"), joinShort(fs, 8))
        if (managed) InfoRow(t("info_source"), if (e.source == Model.SRC_GITHUB) "github.com/" + Modules.repoOf(e.id) else sourceName(e.source) + " #" + e.id)
    }
    Releases(meta.releases, r.version, if (e != null && e.source == Model.SRC_GITHUB) Modules.repoOf(e.id) else null)
    Spacer(Modifier.height(60.dp))
}

// ── a Browse item ──

@Composable
fun ItemDetail(app: AppState, it: Model.Item?) {
    if (it == null) { DetailHint(t("detail_pick_addon"), t("detail_hint_browse"), "ic_apps"); return }
    app.revision
    val e = app.entryOf(it)
    val ended = indexMeta(it.source, it.id, it)
    Hero(it.iconUrl, it.name ?: it.id, byline(it.author, sourceName(it.source)), it, app) {
        if (e != null) Pill(t("pill_installed"), Palette.rune, Palette.runeSoft, "ic_check")
        if (it.foreverVerified) Pill(t("pill_forever"), Palette.rune, Palette.runeSoft) else Pill(t("pill_forever_unverified"), Palette.warn, Palette.warnSoft)
        if (ended.isDiscontinued) Pill(t("tag_discontinued"), Palette.danger, Palette.dangerSoft)
        if (it.tags.contains(Categories.GAMEPAD)) Pill(t("tag_gamepad"), Palette.gold, Palette.goldSoft)
    }
    // A discontinued addon leads with its notice and the addon to use instead; installing it stays possible.
    Ended(app, ended, true)
    val busy = app.busy[it.key()]
    Actions {
        if (busy != null) BusyBar(busy)
        else {
            val pkg = e == null && it.source == Model.SRC_GITHUB && Index.moduleCount(it.id) > 0
            when {
                e != null && e.updateAvailable -> Button(t("action_update_to", clip(e.latest, 22)), Kind.PRIMARY, icon = "ic_upgrade") { app.update(e) }
                e != null -> Button(t("action_reinstall"), icon = "ic_download") { app.install(it) }
                else -> Button(t(if (pkg) "action_install_all" else "action_install"), if (ended.isDiscontinued && ended.successor != null) Kind.SECONDARY else Kind.PRIMARY,
                    icon = "ic_download", enabled = app.gameReady) { app.install(it) }
            }
        }
        it.pageUrl?.let { url -> Button(t("action_page"), Kind.GHOST, icon = "ic_open") { openUrl(url) } }
        if (e != null && busy == null) Button(t("action_remove"), Kind.DANGER, icon = "ic_delete") { app.removeEntry(e) }
    }
    if (!app.gameReady) Note(t("browse_no_game"), Palette.warn, Palette.warnSoft, "ic_warning")
    if (it.source == Model.SRC_GITHUB) Modules(app, it.id, null)
    it.note?.let { n -> Note(n, Palette.warn, Palette.warnSoft) }
    if (it.source == Model.SRC_WOWI) Note(t("note_wowi_version"), Palette.blue, Palette.blueSoft)

    if (it.summary.isNotEmpty()) Text(it.summary, color = Palette.text, fontSize = 13.5.sp, lineHeight = 19.sp)
    if (it.source == Model.SRC_WOWI) {
        var wowi by remember(it.key()) { mutableStateOf<String?>(null) }
        LaunchedEffect(it.key()) { wowi = withContext(Dispatchers.IO) { try { Sources.Wowi.description(it.id) } catch (x: Exception) { null } } }
        Description(wowi)
    }
    val meta = indexMeta(it.source, it.id, it)
    Screenshots(meta.screenshots)
    Description(meta.description)
    Changelog(it.version, meta.changelog, false)

    Section(null) {
        if (e != null) InfoRow(t("info_installed"), e.version)
        InfoRow(t("info_version"), it.version)
        if (meta.stars >= 0) InfoRow(t("info_stars"), formatCount(meta.stars))
        InfoRow(t("info_category"), it.category, mono = false)
        if (it.downloads > 0) InfoRow(t("info_downloads"), formatCount(it.downloads))
        if (it.updated > 0) InfoRow(t("info_updated"), formatDate(it.updated), mono = false)
        if (it.folders.isNotEmpty()) InfoRow(t("info_folders"), joinShort(it.folders, 6))
        if (it.deps.isNotEmpty()) InfoRow(t("info_dependencies"),
            t("info_dependencies_auto", it.deps.joinToString(", ") { d -> Catalog.byKey(d)?.name ?: d }), mono = false)
        InfoRow(t("info_source"), if (it.source == Model.SRC_GITHUB) "github.com/" + Modules.repoOf(it.id) else sourceName(it.source) + " #" + it.id)
    }
    Releases(meta.releases, e?.version, if (it.source == Model.SRC_GITHUB) Modules.repoOf(it.id) else null)
    Spacer(Modifier.height(60.dp))
}
