package app.wowforever.addons

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

private enum class SettingsPart(val label: String) { GAME("settings_game_folder"), BROWSE("nav_browse"), GITHUB("settings_github"), LANGUAGE("settings_language"), ABOUT("settings_about") }

/** Settings as in the design: anchors on the left, groups of rows on the right, using the full width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: AppState) {
    app.revision
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val tops = remember { mutableStateMapOf<SettingsPart, Int>() }
    val current = SettingsPart.values().lastOrNull { (tops[it] ?: Int.MAX_VALUE) <= scroll.value + 40 } ?: SettingsPart.GAME
    Column(Modifier.fillMaxSize()) {
        Header(t("settings_title"))
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(200.dp).padding(start = 24.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (s in SettingsPart.values()) {
                    val on = s == current
                    Text(t(s.label), color = if (on) Palette.text else Palette.text2, fontSize = 13.5.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) Palette.surface2 else Color.Transparent)
                            .pointerHoverIcon(PointerIcon.Hand).clickable { scope.launch { scroll.animateScrollTo(tops[s] ?: 0) } }
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 8.dp, end = 28.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    fun Modifier.anchor(s: SettingsPart) = onGloballyPositioned { tops[s] = it.positionInParent().y.toInt() }

                    Group(t("settings_game_folder"), Modifier.anchor(SettingsPart.GAME)) {
                        val root = app.gameRoot
                        SettingRow(
                            icon = "ic_folder",
                            title = if (root == null) t("settings_not_selected") else File(root).name.ifEmpty { root },
                            sub = app.store.addonsDir()?.let { t(if (it.isDirectory) "settings_addons_to" else "settings_addons_to_new", it.absolutePath) } ?: t("settings_pick_game"),
                            mono = true,
                        ) {
                            Button(t("game_auto_detect"), Kind.GHOST, icon = "ic_search", small = true) { app.detect() }
                            Spacer(Modifier.width(8.dp))
                            Button(t("game_pick_folder"), small = true, icon = "ic_folder") { pickFolder()?.let(app::setGame) }
                        }
                        if (root != null && !app.gameReady) Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Note(t("settings_game_missing"), Palette.warn, Palette.warnSoft, "ic_warning") }
                        if (app.found.size > 1) {
                            Divider()
                            Text(t("game_multiple_found"), color = Palette.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                            for (f in app.found) SettingRow("ic_folder", f.name, f.absolutePath, mono = true, onClick = { app.setGame(f) }) {
                                Icon(drawable("ic_chevron"), null, Modifier.size(18.dp), tint = Palette.text3)
                            }
                        }
                    }

                    Group(t("nav_browse"), Modifier.anchor(SettingsPart.BROWSE)) {
                        SettingRow("ic_check", t("filter_verified") + " ✓", t("filter_verified_on")) {
                            Switch(app.verifiedOnly) { app.setVerifiedOnly(it) }
                        }
                    }

                    Group(t("settings_github"), Modifier.anchor(SettingsPart.GITHUB)) {
                        GithubGroup(app)
                    }

                    Group(t("settings_language"), Modifier.anchor(SettingsPart.LANGUAGE)) {
                        Text(t("settings_language_text"), color = Palette.text3, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(t("language_system"), app.language.isEmpty(), "ic_language") { app.changeLanguage("") }
                            for ((tag, name) in DesktopTexts.NAMES) Chip(name, app.language == tag) { app.changeLanguage(tag) }
                        }
                    }

                    Group("Runeloader", Modifier.anchor(SettingsPart.ABOUT)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(drawable("wordmark"), t("app_name"), Modifier.height(30.dp))
                            Text(t("settings_version", t("app_name"), APP_VERSION), color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(t("settings_about_text"), color = Palette.text2, fontSize = 13.sp, lineHeight = 19.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(t("action_page"), Kind.SECONDARY, icon = "ic_open", small = true) { openUrl("https://github.com/Gulliby-development/runeloader") }
                                Button(t("action_clear_cache"), Kind.GHOST, icon = "ic_delete", small = true) { app.clearCache() }
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 6.dp), style = scrollbarStyle())
            }
        }
    }
}

@Composable
private fun Group(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(12.dp)) {
        Label(title, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        content()
    }
}

@Composable
private fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.stroke.copy(alpha = 0.6f))) }

@Composable
private fun SettingRow(icon: String?, title: String, sub: String?, mono: Boolean = false, onClick: (() -> Unit)? = null,
                       trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().let { if (onClick != null) it.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else it }
        .padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(drawable(icon), null, Modifier.size(18.dp), tint = Palette.rune); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = Palette.text3, fontFamily = if (mono) Fonts.mono else Fonts.body, fontSize = if (mono) 11.5.sp else 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** The user's own GitHub token: the field, save and remove, and what GitHub said about the limit. */
@Composable
private fun ColumnScope.GithubGroup(app: AppState) {
    var token by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { app.refreshRate() }
    Text(t("settings_github_text"), color = Palette.text3, fontSize = 12.5.sp, lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    SettingRow("ic_link", if (app.hasToken) t("github_token_set") else t("github_token_hint"),
        if (app.rateLimit > 0) t("github_requests_left", app.rateLeft.coerceAtLeast(0), app.rateLimit) else null) {
        if (app.hasToken) Button(t("action_remove"), Kind.DANGER, icon = "ic_delete", small = true) { app.removeToken() }
    }
    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(token, t("github_token_hint"), Modifier.weight(1f), icon = null, secret = true) { token = it.trim() }
        Spacer(Modifier.width(8.dp))
        Button(t("action_save"), Kind.PRIMARY, small = true, enabled = token.isNotEmpty() && !app.tokenBusy) { app.saveToken(token); token = "" }
        Spacer(Modifier.width(8.dp))
        Button(t("action_create_token"), Kind.GHOST, icon = "ic_open", small = true) {
            openUrl("https://github.com/settings/personal-access-tokens/new?name=Runeloader&description=Runeloader+addon+updates")
        }
    }
}
