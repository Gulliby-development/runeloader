package app.wowforever.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The building blocks of the design: glass panels, buttons, chips, mono labels.

enum class Kind { PRIMARY, SECONDARY, GHOST, DANGER }

/** A panel of the design: a faint light from the top edge over the surface, a thin stroke. */
fun Modifier.glass(radius: Dp = 12.dp, highlight: Color = Color.White.copy(alpha = 0.035f), stroke: Color = Palette.stroke): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape)
        .background(Palette.surface)
        .background(Brush.verticalGradient(listOf(highlight, Color.Transparent)))
        .border(1.dp, Brush.verticalGradient(listOf(stroke.copy(alpha = 1f), stroke.copy(alpha = 0.55f))), shape)
}

/** The mint glow from the top of a pane, like the light of the rune. */
fun Modifier.runeGlow(strength: Float = 0.08f): Modifier = drawBehind {
    drawRect(Brush.radialGradient(
        listOf(Palette.rune.copy(alpha = strength), Color.Transparent),
        center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.7f,
    ))
}

@Composable
fun Button(text: String?, kind: Kind = Kind.SECONDARY, icon: String? = null, small: Boolean = false,
           enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (small) 7.dp else 9.dp)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val (fill, ink, edge) = when (kind) {
        Kind.PRIMARY -> Triple(if (hovered) Color(0xFFE6B456) else Palette.gold, Palette.goldInk, Color.Transparent)
        Kind.SECONDARY -> Triple(if (hovered) Palette.surface3 else Palette.surface2, Palette.text, Palette.stroke)
        Kind.GHOST -> Triple(if (hovered) Palette.surface2 else Color.Transparent, Palette.text2, Palette.stroke)
        Kind.DANGER -> Triple(if (hovered) Palette.dangerSoft else Color.Transparent, Palette.danger, Palette.danger.copy(alpha = 0.35f))
    }
    var m = Modifier.alpha(if (enabled) 1f else 0.45f)
    if (kind == Kind.PRIMARY && enabled) m = m.shadow(10.dp, shape, ambientColor = Palette.gold.copy(alpha = 0.35f), spotColor = Palette.gold.copy(alpha = 0.45f))
    Row(
        m.clip(shape).background(fill).border(1.dp, edge, shape)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .height(if (small) 28.dp else 34.dp)
            .padding(horizontal = if (text == null) (if (small) 7.dp else 9.dp) else if (small) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(drawable(icon), text, Modifier.size(if (small) 14.dp else 16.dp), tint = ink)
        if (icon != null && text != null) Spacer(Modifier.width(7.dp))
        if (text != null) Text(text, color = ink, fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A mono, letter-spaced, upper-case label: section and column heads. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Palette.text3) {
    Text(text.uppercase(Texts.locale()), modifier, color = color, fontFamily = Fonts.mono, fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A small mono tag: the source, a flavor, a state. */
@Composable
fun Tag(text: String, fg: Color = Palette.text2, bg: Color = Palette.surface3) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = fg, fontFamily = Fonts.mono, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

/** A rounded status pill, with an optional icon. */
@Composable
fun Pill(text: String, fg: Color = Palette.text2, bg: Color = Palette.surface3, icon: String? = null) {
    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(drawable(icon), null, Modifier.size(13.dp), tint = fg); Spacer(Modifier.width(5.dp)) }
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A coloured state line with an icon: "✓ Up to date", "⚠ Discontinued". */
@Composable
fun StateText(text: String, color: Color, icon: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(drawable(icon), null, Modifier.size(15.dp), tint = color); Spacer(Modifier.width(5.dp)) }
        Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A filter chip; the chosen one glows gold. */
@Composable
fun Chip(text: String, on: Boolean, icon: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(99.dp)
    Row(
        Modifier.clip(shape).background(if (on) Palette.goldSoft else Color.Transparent)
            .border(1.dp, if (on) Palette.gold.copy(alpha = 0.5f) else Palette.stroke, shape)
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (on) Palette.gold else Palette.text2
        if (icon != null) { Icon(drawable(icon), null, Modifier.size(14.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, fontSize = 12.sp, maxLines = 1)
    }
}

/** A segmented switch: "All / Updates / Favorites", "Popular / Categories / Favorites". */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, icon: (T) -> String? = { null }, onPick: (T) -> Unit) {
    Row(Modifier.glass(10.dp).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (o in options) {
            val on = o == selected
            Row(
                Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) Palette.surface3 else Color.Transparent)
                    .pointerHoverIcon(PointerIcon.Hand).clickable { onPick(o) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon(o)?.let { Icon(drawable(it), null, Modifier.size(14.dp), tint = if (on) Palette.gold else Palette.text3); Spacer(Modifier.width(6.dp)) }
                Text(label(o), color = if (on) Palette.text else Palette.text2, fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** A text field with a leading icon; big for the Browse search. */
@Composable
fun Field(value: String, hint: String, modifier: Modifier = Modifier, big: Boolean = false, onChange: (String) -> Unit) {
    Row(modifier.height(if (big) 44.dp else 34.dp).glass(if (big) 11.dp else 9.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(drawable("ic_search"), null, Modifier.size(if (big) 18.dp else 16.dp), tint = Palette.text3)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val size = if (big) 15.sp else 13.5.sp
            if (value.isEmpty()) Text(hint, color = Palette.text3, fontSize = size, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(value, onChange, singleLine = true, cursorBrush = SolidColor(Palette.gold),
                textStyle = TextStyle(color = Palette.text, fontSize = size, fontFamily = Fonts.body), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Icon(drawable("ic_close"), null, Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
            .pointerHoverIcon(PointerIcon.Hand).clickable { onChange("") }, tint = Palette.text3)
    }
}

/** A pill switch, mint when on, as in the design's settings. */
@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(38.dp, 22.dp).clip(RoundedCornerShape(99.dp)).background(if (on) Palette.rune else Palette.surface3)
            .border(1.dp, if (on) Palette.rune else Palette.stroke, RoundedCornerShape(99.dp))
            .pointerHoverIcon(PointerIcon.Hand).clickable { onChange(!on) }.padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(99.dp)).background(if (on) Palette.bg else Palette.text3))
    }
}

/** A thin progress bar for a running install. */
@Composable
fun BusyBar(text: String) {
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.width(120.dp).height(5.dp).clip(RoundedCornerShape(9.dp)).background(Palette.surface3)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.6f).clip(RoundedCornerShape(9.dp))
                .background(Brush.horizontalGradient(listOf(Palette.gold.copy(alpha = 0.6f), Palette.gold))))
        }
        Spacer(Modifier.height(4.dp))
        Text(text, color = Palette.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
    }
}

/** A page title in the display font, with a line under it. */
@Composable
fun Header(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontFamily = Fonts.display, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Palette.text3, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun Empty(icon: String, title: String, text: String, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Palette.surface2)
            .border(1.dp, Palette.stroke, RoundedCornerShape(20.dp)).runeGlow(0.18f), contentAlignment = Alignment.Center) {
            Icon(drawable(icon), null, Modifier.size(34.dp), tint = Palette.rune)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = Palette.text, fontFamily = Fonts.display, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Palette.text2, fontSize = 13.5.sp, modifier = Modifier.widthIn(max = 460.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

/** A note in a soft colour: Forever caveats, check errors, a discontinued notice. */
@Composable
fun Note(text: String, fg: Color, bg: Color, icon: String = "ic_info") {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
        .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Icon(drawable(icon), null, Modifier.size(17.dp), tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, fontSize = 12.5.sp)
    }
}
