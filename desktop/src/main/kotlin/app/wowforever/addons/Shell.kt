package app.wowforever.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import java.io.File

/** The window: the sidebar on the left, the chosen section on the right. */
@Composable
fun Shell(app: AppState) {
    Row(Modifier.fillMaxSize().background(Palette.bg)) {
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
}

@Composable
private fun Sidebar(app: AppState) {
    Column(Modifier.width(236.dp).fillMaxHeight().background(Palette.rail).padding(16.dp)) {
        Image(drawable("wordmark"), t("app_name"), Modifier.padding(start = 6.dp, top = 6.dp, bottom = 22.dp).height(26.dp))
        for (tab in Tab.values()) NavItem(tab, app.tab == tab) { app.tab = tab }
        Spacer(Modifier.weight(1f))
        GameCard(app)
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
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
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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
fun Button(text: String, primary: Boolean = false, icon: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.clip(shape).background(if (primary) Palette.gold else Palette.surface2)
            .border(1.dp, if (primary) Palette.gold else Palette.stroke, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(drawable(icon), null, Modifier.size(18.dp), tint = if (primary) Palette.goldInk else Palette.text2)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (primary) Palette.goldInk else Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
fun ListRow(icon: @Composable () -> Unit, title: String, subtitle: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(10.dp))
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Palette.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, color = Palette.text2, fontSize = 13.sp)
    }
}
