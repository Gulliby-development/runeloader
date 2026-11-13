package app.wowforever.addons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

/** The window: the sidebar on the left, the chosen section on the right. */
@Composable
fun Shell(app: AppState) {
    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(app)
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.stroke))
            Box(Modifier.weight(1f).fillMaxHeight().runeGlow(0.06f)) {
                when (app.tab) {
                    Tab.LIBRARY -> LibraryScreen(app)
                    Tab.BROWSE -> BrowseScreen(app)
                    Tab.SETTINGS -> SettingsScreen(app)
                }
            }
        }
        Snackbar(app, Modifier.align(Alignment.BottomCenter))
        app.confirmRemove?.let { RemoveDialog(app, it) }
    }
}

@Composable
private fun Snackbar(app: AppState, modifier: Modifier) {
    val text = app.message ?: return
    LaunchedEffect(text) { delay(5000); if (app.message == text) app.message = null }
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.padding(bottom = 24.dp).widthIn(max = 640.dp)
        .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape).background(Color(0xFF223F38)).border(1.dp, Palette.stroke, shape)
        .clickable { app.message = null }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(drawable("ic_info"), null, Modifier.size(17.dp), tint = Palette.rune)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Palette.text, fontSize = 13.5.sp)
    }
}

@Composable
private fun RemoveDialog(app: AppState, r: Model.Installed) {
    val folders = Library.removeFolders(r)
    AlertDialog(
        onDismissRequest = { app.confirmRemove = null },
        containerColor = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        title = { Text(t("remove_title", r.title), color = Palette.text, fontFamily = Fonts.display, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text(t("remove_text", joinShort(folders, 10)), color = Palette.text2, fontSize = 13.5.sp) },
        confirmButton = { Button(t("action_remove"), Kind.DANGER, icon = "ic_delete") { app.remove(r) } },
        dismissButton = { Button(t("action_cancel"), Kind.GHOST) { app.confirmRemove = null } },
    )
}

@Composable
private fun Sidebar(app: AppState) {
    Column(Modifier.width(236.dp).fillMaxHeight().background(Palette.rail).padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
        Image(drawable("wordmark"), t("app_name"), Modifier.padding(start = 10.dp, top = 2.dp, bottom = 18.dp).height(30.dp))
        for (tab in Tab.values()) NavItem(tab, app.tab == tab, if (tab == Tab.LIBRARY) app.updates else 0) { app.tab = tab }
        Label(t("settings_game_folder"), Modifier.padding(start = 12.dp, top = 22.dp, bottom = 8.dp))
        GameCard(app)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.stroke))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (app.indexSize > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(Palette.rune))
                Spacer(Modifier.width(7.dp))
                Text(t("sidebar_index", q("addons_count", app.indexSize)), color = Palette.text3, fontSize = 11.5.sp, maxLines = 1)
            }
            Text(t("settings_version", t("app_name"), APP_VERSION), color = Palette.text3, fontSize = 11.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape)
            .background(if (selected) Palette.surface2 else Color.Transparent)
            .drawBehind { if (selected) drawRect(Palette.gold, size = size.copy(width = 3.dp.toPx())) }
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(drawable(tab.icon), null, Modifier.size(19.dp), tint = if (selected) Palette.gold else Palette.text3)
        Spacer(Modifier.width(12.dp))
        Text(t(tab.label), color = if (selected) Palette.text else Palette.text2, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f))
        if (badge > 0) Box(Modifier.clip(RoundedCornerShape(99.dp)).background(Palette.gold).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Text(badge.toString(), color = Palette.goldInk, fontFamily = Fonts.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GameCard(app: AppState) {
    val root = app.gameRoot
    Column(
        Modifier.fillMaxWidth().glass(10.dp).pointerHoverIcon(PointerIcon.Hand)
            .clickable { app.tab = Tab.SETTINGS }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (root == null) t("settings_not_selected") else File(root).name.ifEmpty { root },
                color = if (app.gameReady) Palette.text else Palette.warn, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (app.gameReady && app.store.addonsDir()?.path?.contains("_forever_", ignoreCase = true) == true) {
                Spacer(Modifier.width(8.dp))
                Tag("FOREVER", Palette.rune, Palette.runeSoft)
            }
        }
        val path = app.store.addonsDir()?.absolutePath ?: t("settings_pick_game")
        Text(path, color = Palette.text3, fontFamily = Fonts.mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The detail column on the right, as in the design (372 dp). */
@Composable
fun DetailPane(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.stroke))
    val scroll = androidx.compose.foundation.rememberScrollState()
    Box(Modifier.width(372.dp).fillMaxHeight().background(Palette.rail)
        .background(Brush.verticalGradient(listOf(Palette.rune.copy(alpha = 0.05f), Color.Transparent), endY = 420f))) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        androidx.compose.foundation.VerticalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(scroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp), style = scrollbarStyle())
    }
}

fun scrollbarStyle() = androidx.compose.foundation.ScrollbarStyle(
    minimalHeight = 24.dp, thickness = 6.dp, shape = RoundedCornerShape(3.dp), hoverDurationMillis = 200,
    unhoverColor = Palette.stroke, hoverColor = Palette.text3,
)

