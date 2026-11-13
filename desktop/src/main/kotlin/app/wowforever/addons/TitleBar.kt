package app.wowforever.addons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Cursor
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.WindowEvent
import java.awt.MouseInfo
import kotlin.math.max

/**
 * The window frame in the brand style, instead of the system one: the window is undecorated, so the
 * title bar, its buttons, moving, double-click to maximize and resizing by the edges are ours.
 */
class Chrome(private val window: java.awt.Window) {
    var maximized by mutableStateOf(false)
        private set
    private var restore: Rectangle? = null

    /** Maximizes to the screen's work area, so the taskbar stays visible. */
    fun toggleMaximize() {
        if (maximized) {
            restore?.let { window.bounds = it }
            maximized = false
        } else {
            restore = window.bounds
            val gc = window.graphicsConfiguration
            val insets = window.toolkit.getScreenInsets(gc)
            val b = gc.bounds
            window.bounds = Rectangle(b.x + insets.left, b.y + insets.top,
                b.width - insets.left - insets.right, b.height - insets.top - insets.bottom)
            maximized = true
        }
    }

    fun minimize() { (window as? Frame)?.extendedState = Frame.ICONIFIED }

    fun close() { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) }

    /** Moves the window by a drag; a maximized window first goes back to its size under the cursor. */
    fun dragStart() {
        if (!maximized) return
        val old = restore ?: return
        val mouse = MouseInfo.getPointerInfo().location
        val share = (mouse.x - window.x).toDouble() / max(1, window.width)
        maximized = false
        window.bounds = Rectangle((mouse.x - old.width * share).toInt(), window.y, old.width, old.height)
    }

    fun moveBy(dx: Int, dy: Int) {
        if (maximized) return
        window.location = Point(window.x + dx, window.y + dy)
    }

    /** Resizes from an edge: sx, sy are -1 (left/top), 0 or 1 (right/bottom). */
    fun resizeBy(sx: Int, sy: Int, dx: Int, dy: Int) {
        if (maximized) return
        val min = window.minimumSize
        val r = window.bounds
        if (sx > 0) r.width = max(min.width, r.width + dx)
        if (sy > 0) r.height = max(min.height, r.height + dy)
        if (sx < 0) { val w = max(min.width, r.width - dx); r.x += r.width - w; r.width = w }
        if (sy < 0) { val h = max(min.height, r.height - dy); r.y += r.height - h; r.height = h }
        window.bounds = r
    }
}

@Composable
fun FrameWindowScope.TitleBar(chrome: Chrome) {
    Row(Modifier.fillMaxWidth().height(36.dp).background(Palette.rail), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { chrome.toggleMaximize() }) }
                .pointerInput(Unit) {
                    // screen coordinates: the window moves under the pointer while it is dragged
                    var from: Point? = null
                    detectDragGestures(
                        onDragStart = { chrome.dragStart(); from = MouseInfo.getPointerInfo().location },
                        onDragEnd = { from = null },
                        onDragCancel = { from = null },
                    ) { change, _ ->
                        change.consume()
                        val p = MouseInfo.getPointerInfo().location
                        from?.let { chrome.moveBy(p.x - it.x, p.y - it.y) }
                        from = p
                    }
                }
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource("icon.png"), null, Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(t("app_name"), color = Palette.text3, fontSize = 12.sp)
        }
        ChromeButton(onClick = chrome::minimize) { c -> drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.2f) }
        ChromeButton(onClick = chrome::toggleMaximize) { c ->
            val s = Stroke(1.2f)
            if (chrome.maximized) {
                val d = size.width * 0.22f
                drawRect(c, Offset(d, 0f), Size(size.width - d, size.height - d), style = s)
                drawRect(Palette.rail, Offset(0f, d), Size(size.width - d, size.height - d))
                drawRect(c, Offset(0f, d), Size(size.width - d, size.height - d), style = s)
            } else drawRect(c, Offset.Zero, size, style = s)
        }
        ChromeButton(hover = Palette.danger, onClick = chrome::close) { c ->
            drawLine(c, Offset.Zero, Offset(size.width, size.height), 1.3f)
            drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), 1.3f)
        }
    }
}

@Composable
private fun ChromeButton(hover: Color = Palette.surface3, onClick: () -> Unit, icon: DrawScope.(Color) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier.width(46.dp).fillMaxHeight().hoverable(source)
            .background(if (hovered) hover else Color.Transparent)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val c = if (hovered) Palette.text else Palette.text2
        Canvas(Modifier.size(10.dp)) { icon(c) }
    }
}

/** Thin areas along the edges and corners that resize the undecorated window. */
@Composable
fun BoxScope.ResizeEdges(chrome: Chrome) {
    if (chrome.maximized) return
    val e = 5.dp
    val c = 10.dp
    @Composable
    fun edge(m: Modifier, sx: Int, sy: Int, cursor: Int) {
        Box(m.pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(sx, sy) {
            var from: Point? = null
            detectDragGestures(
                onDragStart = { from = MouseInfo.getPointerInfo().location },
                onDragEnd = { from = null },
                onDragCancel = { from = null },
            ) { change, _ ->
                change.consume()
                val p = MouseInfo.getPointerInfo().location
                from?.let { chrome.resizeBy(sx, sy, p.x - it.x, p.y - it.y) }
                from = p
            }
        })
    }
    edge(Modifier.align(Alignment.CenterStart).width(e).fillMaxHeight(), -1, 0, Cursor.W_RESIZE_CURSOR)
    edge(Modifier.align(Alignment.CenterEnd).width(e).fillMaxHeight(), 1, 0, Cursor.E_RESIZE_CURSOR)
    edge(Modifier.align(Alignment.TopCenter).height(e).fillMaxWidth(), 0, -1, Cursor.N_RESIZE_CURSOR)
    edge(Modifier.align(Alignment.BottomCenter).height(e).fillMaxWidth(), 0, 1, Cursor.S_RESIZE_CURSOR)
    edge(Modifier.align(Alignment.TopStart).size(c), -1, -1, Cursor.NW_RESIZE_CURSOR)
    edge(Modifier.align(Alignment.TopEnd).size(c), 1, -1, Cursor.NE_RESIZE_CURSOR)
    edge(Modifier.align(Alignment.BottomStart).size(c), -1, 1, Cursor.SW_RESIZE_CURSOR)
    edge(Modifier.align(Alignment.BottomEnd).size(c), 1, 1, Cursor.SE_RESIZE_CURSOR)
}
