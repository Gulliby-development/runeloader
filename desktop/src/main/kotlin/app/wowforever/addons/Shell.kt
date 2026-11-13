package app.wowforever.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import java.io.File
import kotlinx.coroutines.delay

/** The window: the sidebar on the left, the chosen section on the right. */
@Composable
fun Shell(app: AppState) {
    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(app)
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.stroke))
            Box(Modifier.weight(1f).fillMaxHeight()) {
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
    Row(modifier.padding(bottom = 24.dp).widthIn(max = 640.dp).clip(RoundedCornerShape(10.dp))
        .background(Palette.surface3).border(1.dp, Palette.stroke, RoundedCornerShape(10.dp))
        .clickable { app.message = null }.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(text, color = Palette.text, fontSize = 14.sp)
    }
}

@Composable
private fun RemoveDialog(app: AppState, r: Model.Installed) {
    val folders = Library.removeFolders(r)
    val shown = if (folders.size > 10) folders.take(10).joinToString(", ") + ", …" else folders.joinToString(", ")
    AlertDialog(
        onDismissRequest = { app.confirmRemove = null },
        containerColor = Palette.surface,
        title = { Text(t("remove_title", r.title), color = Palette.text) },
        text = { Text(t("remove_text", shown), color = Palette.text2) },
        confirmButton = { Button(t("action_remove"), danger = true) { app.remove(r) } },
        dismissButton = { Button(t("action_cancel")) { app.confirmRemove = null } },
    )
}

@Composable
private fun Sidebar(app: AppState) {
    Column(Modifier.width(236.dp).fillMaxHeight().background(Palette.rail).padding(16.dp)) {
        Image(drawable("wordmark"), t("app_name"), Modifier.padding(start = 6.dp, top = 6.dp, bottom = 22.dp).height(26.dp))
        for (tab in Tab.values()) NavItem(tab, app.tab == tab, if (tab == Tab.LIBRARY) app.updates else 0) { app.tab = tab }
        Spacer(Modifier.weight(1f))
        GameCard(app)
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape)
            .background(if (selected) Palette.surface2 else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(drawable(tab.icon), null, Modifier.size(20.dp), tint = if (selected) Palette.gold else Palette.text3)
        Spacer(Modifier.width(12.dp))
        Text(t(tab.label), color = if (selected) Palette.text else Palette.text2, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (badge > 0) Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Palette.gold).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Text(badge.toString(), color = Palette.goldInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameCard(app: AppState) {
    val root = app.gameRoot
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.surface)
            .border(1.dp, Palette.stroke, RoundedCornerShape(12.dp))
            .clickable { app.tab = Tab.SETTINGS }.padding(12.dp),
    ) {
        Text(t("settings_game_folder"), color = Palette.text3, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (root == null) t("settings_not_selected") else File(root).name.ifEmpty { root },
            color = if (app.gameReady) Palette.text else Palette.warn, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── shared pieces of the sections ──

@Composable
fun Header(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Palette.text3, fontSize = 13.sp)
        }
        actions()
    }
}

@Composable
fun Button(text: String, primary: Boolean = false, icon: String? = null, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val fill = when { danger -> Palette.danger; primary -> Palette.gold; else -> Palette.surface2 }
    Row(
        Modifier.alpha(if (enabled) 1f else 0.5f).clip(shape).background(fill)
            .border(1.dp, if (primary || danger) fill else Palette.stroke, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (primary || danger) Palette.goldInk else Palette.text
        if (icon != null) {
            Icon(drawable(icon), null, Modifier.size(18.dp), tint = if (primary || danger) ink else Palette.text2)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun Empty(icon: String, title: String, text: String, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(drawable(icon), null, Modifier.size(48.dp), tint = Palette.text3)
        Spacer(Modifier.height(16.dp))
        Text(title, color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Palette.text2, fontSize = 14.sp, modifier = Modifier.widthIn(max = 440.dp))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

/** The first letter of a name on a coloured square, until addon icons load (M2). */
@Composable
fun LetterIcon(name: String) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.surface3), contentAlignment = Alignment.Center) {
        Text(letter.toString(), color = Palette.gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ListRow(icon: @Composable () -> Unit, title: String, subtitle: String, selected: Boolean = false,
            onClick: () -> Unit = {}, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 1.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) Palette.surface2 else Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Palette.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

/** A small rounded label: a version, a state. */
@Composable
fun Pill(text: String, color: Color = Palette.text2) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Palette.stroke, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 12.sp, maxLines = 1)
    }
}
