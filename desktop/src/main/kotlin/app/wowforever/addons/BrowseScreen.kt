package app.wowforever.addons

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowseScreen(app: AppState) {
    LaunchedEffect(Unit) { app.ensureResults() }
    app.revision
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val hint = when {
                    app.browseMode == BrowseMode.FAVORITES -> t("browse_hint_favorites")
                    app.browseMode == BrowseMode.CATEGORIES && app.category != null -> t("browse_hint_category")
                    else -> t("browse_hint_github")
                }
                Field(app.query, hint, Modifier.weight(1f), big = true) { app.changeQuery(it) }
                Spacer(Modifier.width(12.dp))
                Segmented(BrowseMode.values().toList(), app.browseMode, { t(it.label) }, { it.icon }) { app.setBrowseMode(it) }
            }
            if (app.browseMode != BrowseMode.FAVORITES) Filters(app)
            if (!app.gameReady) GameBanner(app)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (app.showGrid) CategoryGrid(app) else Results(app)
            }
        }
        val item = app.selectedItem
        if (item != null) DetailPane {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(null, Kind.GHOST, icon = "ic_close", small = true) { app.selectedItem = null }
            }
            ItemDetail(app, item)
        }
    }
}

/** Category chips and the Forever filter; in Categories mode a back chip to the grid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(app: AppState) {
    FlowRow(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (app.browseMode == BrowseMode.CATEGORIES) {
            Chip(t("browse_all_categories"), app.category == null, "ic_back") { app.changeQuery(""); app.setBrowseMode(BrowseMode.CATEGORIES, null) }
            app.category?.let { Chip(t(Categories.nameKey(it)), true) {} }
        } else {
            Chip(t("filter_all"), app.category == null) { app.setBrowseMode(BrowseMode.POPULAR, null) }
            for (k in Categories.KEYS) if (k != Categories.OTHER)
                Chip(t(Categories.nameKey(k)), app.category == k) {
                    app.setBrowseMode(BrowseMode.POPULAR, if (app.category == k) null else k)
                }
        }
        Spacer(Modifier.width(8.dp))
        val on = app.verifiedOnly
        Row(
            Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Palette.runeSoft else Color.Transparent)
                .border(1.dp, if (on) Palette.rune.copy(alpha = 0.6f) else Palette.stroke, RoundedCornerShape(99.dp))
                .pointerHoverIcon(PointerIcon.Hand).clickable { app.setVerifiedOnly(!on) }.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(drawable("ic_check"), null, Modifier.size(14.dp), tint = if (on) Palette.rune else Palette.text3)
            Spacer(Modifier.width(6.dp))
            Text(t("filter_verified"), color = if (on) Palette.rune else Palette.text2, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GameBanner(app: AppState) {
    Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(Palette.warnSoft).border(1.dp, Palette.warn.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(drawable("ic_warning"), null, Modifier.size(17.dp), tint = Palette.warn)
        Spacer(Modifier.width(10.dp))
        Text(t("browse_no_game"), color = Palette.warn, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Button(t("browse_set_folder"), Kind.GHOST, small = true) { pickFolder()?.let(app::setGame) }
    }
}

@Composable
private fun CategoryGrid(app: AppState) {
    LaunchedEffect(app.verifiedOnly) { app.loadCounts() }
    val counts = app.counts
    LazyVerticalGrid(GridCells.Adaptive(180.dp), Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(Categories.order(false).toList()) { key ->
            HoverCard(selected = false, onClick = { app.setBrowseMode(BrowseMode.CATEGORIES, key) }) {
                CategoryIcon(key, 44.dp)
                Spacer(Modifier.height(12.dp))
                Text(t(Categories.nameKey(key)), color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2)
                val n = counts?.get(key)
                Text(if (n != null) q("addons_count", n) else if (counts == null) "…" else " ", color = Palette.text3,
                    fontFamily = Fonts.mono, fontSize = 11.5.sp)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun Results(app: AppState) {
    val list = app.results
    if (list.isEmpty()) {
        when {
            app.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.gold, strokeWidth = 3.dp) }
            app.resultError != null -> Empty("ic_warning", t("load_failed"), app.resultError!!) {
                Button(t("action_retry"), Kind.PRIMARY, icon = "ic_refresh") { app.search(false) }
            }
            app.browseMode == BrowseMode.FAVORITES && app.query.isBlank() -> Empty("ic_star_outline", t("favorites_empty_title"), t("favorites_empty_text"))
            app.browseMode == BrowseMode.FAVORITES -> Empty("ic_search", t("nothing_found"), t("favorites_no_match"))
            app.verifiedOnly -> Empty("ic_search", t("nothing_found"), t("verified_only_empty")) {
                Button(t("action_show_all"), Kind.PRIMARY, icon = "ic_apps") { app.setVerifiedOnly(false) }
            }
            else -> Empty("ic_search", t("nothing_found"), t(if (app.category != null) "category_empty_text" else "github_empty_text"))
        }
        return
    }
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(270.dp), Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp), state,
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list, key = { it.key() }) { item -> ItemCard(app, item) }
            if (app.hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(list.size) { app.search(true) }
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Palette.gold, strokeWidth = 2.dp)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(60.dp)) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp), style = scrollbarStyle())
    }
}

/** A glass card that lifts on hover; the chosen one gets a gold edge and glow, as the design's featured card. */
@Composable
fun HoverCard(selected: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    var m = Modifier.fillMaxWidth()
    if (selected) m = m.shadow(22.dp, shape, ambientColor = Palette.gold.copy(alpha = 0.35f), spotColor = Palette.gold.copy(alpha = 0.5f))
    Column(
        m.glass(12.dp, highlight = Color.White.copy(alpha = if (hovered) 0.06f else 0.035f),
            stroke = when { selected -> Palette.gold.copy(alpha = 0.6f); hovered -> Palette.text3.copy(alpha = 0.7f); else -> Palette.stroke })
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick).padding(14.dp),
        content = content,
    )
}

@Composable
private fun ItemCard(app: AppState, it: Model.Item) {
    val modules = if (it.source == Model.SRC_GITHUB) Index.moduleCount(it.id) else 0
    HoverCard(app.selectedItem?.key() == it.key(), onClick = { app.selectedItem = it }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AddonIcon(it.iconUrl, it.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.name ?: it.id, color = Palette.text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (app.isFavorite(it)) { Spacer(Modifier.width(6.dp)); Icon(drawable("ic_star"), t("favorite_on"), Modifier.size(14.dp), tint = Palette.gold) }
                }
                val sub = listOfNotNull(it.author.takeIf { a -> a.isNotEmpty() }, if (modules > 0) q("modules_count", modules) else null).joinToString(" · ")
                Text(sub, color = Palette.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(it.summary, color = Palette.text2, fontSize = 12.5.sp, lineHeight = 18.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The action keeps its full width; the stats and tags give way and are cut at the edge.
            Row(Modifier.weight(1f).clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
                val stats = listOfNotNull(
                    if (it.downloads > 0) "↓ " + formatCount(it.downloads) else null,
                    if (it.stars > 0) "★ " + formatCount(it.stars) else null,
                ).joinToString("  ")
                if (stats.isNotEmpty()) { Text(stats, color = Palette.text3, fontFamily = Fonts.mono, fontSize = 11.5.sp, maxLines = 1, softWrap = false); Spacer(Modifier.width(8.dp)) }
                if (it.source == Model.SRC_WOWI) Tag("WoWI", Palette.blue, Palette.blueSoft) else Tag("GitHub")
                if (it.tags.contains(Categories.GAMEPAD)) { Spacer(Modifier.width(5.dp)); Tag(t("tag_gamepad"), Palette.rune, Palette.runeSoft) }
                if (it.isDiscontinued) { Spacer(Modifier.width(5.dp)); Tag(t("tag_discontinued"), Palette.danger, Palette.dangerSoft) }
            }
            Spacer(Modifier.width(8.dp))
            CardAction(app, it)
        }
    }
}

@Composable
private fun CardAction(app: AppState, item: Model.Item) {
    val busy = app.busy[item.key()]
    val entry = app.entryOf(item)
    when {
        busy != null -> Pill(t("pill_installing"), Palette.gold, Palette.goldSoft)
        entry != null && entry.updateAvailable -> Button(t("action_update"), Kind.SECONDARY, icon = "ic_refresh", small = true) { app.update(entry) }
        entry != null -> StateText(t("pill_installed"), Palette.rune, "ic_check")
        else -> Button(t("action_install"), Kind.PRIMARY, icon = "ic_download", small = true, enabled = app.gameReady) { app.install(item) }
    }
}
