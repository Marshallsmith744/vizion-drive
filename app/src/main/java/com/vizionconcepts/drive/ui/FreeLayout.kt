package com.vizionconcepts.drive.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizionconcepts.drive.AppContainer
import com.vizionconcepts.drive.dashboard.DashboardLayout
import com.vizionconcepts.drive.dashboard.Placement
import kotlinx.coroutines.launch

/**
 * Swipeable pages of freely placed widgets. Long-press any widget (or just drag it in edit mode)
 * to move it; widgets it lands on shuffle down. The corner handle resizes in proportion.
 */
@Composable
fun FreePages(ui: UiState, c: AppContainer, modifier: Modifier, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val editing by c.editLayout.collectAsState()
    val saved = ui.settings.layout
    var draft by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { draft = saved }
    val scope = rememberCoroutineScope()
    fun commit(l: DashboardLayout) {
        draft = l
        scope.launch { c.settings.update { it.copy(layout = l) } }
    }
    val pager = rememberPagerState { draft.pages.size.coerceAtLeast(1) }
    LaunchedEffect(pager.currentPage) { c.currentPage.value = pager.currentPage }

    Column(modifier) {
        if (editing) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(p.card)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Editing page ${pager.currentPage + 1}", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Drag to move – others shuffle down · corner handle resizes · ✕ removes", color = p.dim, fontSize = 12.sp)
                }
                Chip("+ Add widget") { open(Overlay.EDITOR) }
                Spacer(Modifier.size(6.dp))
                Chip("Pages & presets") { open(Overlay.EDITOR) }
                Spacer(Modifier.size(6.dp))
                Chip("Done", selected = true) { c.editLayout.value = false }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !editing) { index ->
            val items = draft.pages.getOrElse(index) { emptyList() }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wDp = maxWidth
                val wPx = with(LocalDensity.current) { maxWidth.toPx() }
                if (items.isEmpty()) {
                    Card(Modifier.fillMaxSize().clickable { c.editLayout.value = true; open(Overlay.EDITOR) }) {
                        Text("Empty page – tap to add widgets", color = p.dim, modifier = Modifier.align(Alignment.Center))
                    }
                    return@BoxWithConstraints
                }
                // While a widget is being dragged/resized, the others preview where they'd shuffle to.
                var active by remember(index) { mutableStateOf<Int?>(null) }
                var live by remember(index) { mutableStateOf<Placement?>(null) }
                val a = active
                val l = live
                val shown: List<Placement> = if (a != null && l != null && a in items.indices) {
                    val snapped = if (l.w != items[a].w) l.resized(l.w) else l.moved(l.x, l.y)
                    Placement.settle(items.toMutableList().also { it[a] = snapped }, a).toMutableList().also { it[a] = l }
                } else items
                val contentH = wDp * (shown.maxOf { it.bottom } + 0.02f)
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(if (editing || a != null) contentH + 200.dp else contentH)) {
                        // Active widget drawn last so it floats above the others.
                        val drawOrder = items.indices.sortedBy { if (it == a) 1 else 0 }
                        for (i in drawOrder) key(items[i].type, i) {
                            PlacedWidget(
                                shown[i], i == a, wDp, wPx, editing, ui, c, open,
                                onStart = {
                                    active = i; live = items[i]
                                    if (!c.editLayout.value) c.editLayout.value = true
                                },
                                onMove = { dx, dy ->
                                    live = live?.let { it.copy(x = (it.x + dx / wPx).coerceIn(0f, 1f - it.w), y = (it.y + dy / wPx).coerceAtLeast(0f)) }
                                },
                                onResize = { grow ->
                                    live = live?.let { it.copy(w = (it.w + grow / wPx).coerceIn(Placement.MIN_W, 1f - it.x)) }
                                },
                                onEnd = {
                                    val fin = live
                                    if (fin != null) {
                                        val snapped = if (fin.w != items[i].w) fin.resized(fin.w) else fin.moved(fin.x, fin.y)
                                        commit(draft.place(index, i, snapped))
                                    }
                                    active = null; live = null
                                },
                                onDelete = { commit(draft.removeWidget(index, i)) },
                            )
                        }
                    }
                }
            }
        }
        if (draft.pages.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(draft.pages.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) p.accent else p.dim.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacedWidget(
    pl: Placement,
    isActive: Boolean,
    wDp: Dp,
    wPx: Float,
    editing: Boolean,
    ui: UiState,
    c: AppContainer,
    open: (Overlay) -> Unit,
    onStart: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float) -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    // Widgets being shuffled glide to their new spot; the one under your finger follows it exactly.
    val spec = if (isActive) snap<Dp>() else tween<Dp>(220)
    val x by animateDpAsState(wDp * pl.x, spec, label = "x")
    val y by animateDpAsState(wDp * pl.y, spec, label = "y")
    val editingNow by rememberUpdatedState(editing)
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(wDp * pl.w, wDp * pl.h)
            .zIndex(if (isActive) 1f else 0f)
            .graphicsLayer { if (isActive) { scaleX = 1.03f; scaleY = 1.03f; alpha = 0.92f } }
            .padding(6.dp)
            .pointerInput(Unit) {
                // Long-press (or, in edit mode, just drag) to pick a widget up and move it.
                val handle = 56.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val immediate = editingNow
                    if (immediate && down.position.x > size.width - handle && down.position.y > size.height - handle) {
                        return@awaitEachGesture // that's the resize handle
                    }
                    if (immediate) {
                        var moved = false
                        while (!moved) {
                            val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                ?: return@awaitEachGesture
                            if (!ch.pressed) return@awaitEachGesture
                            moved = (ch.position - down.position).getDistance() > viewConfiguration.touchSlop
                        }
                    } else {
                        val finishedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var gone = false
                            while (!gone) {
                                val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                gone = ch == null || !ch.pressed ||
                                    (ch.position - down.position).getDistance() > viewConfiguration.touchSlop
                            }
                            true
                        }
                        if (finishedEarly != null) return@awaitEachGesture // a tap or a scroll, not a long press
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    start()
                    while (true) {
                        val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        val d = ch.position - ch.previousPosition
                        ch.consume() // the widget, page scroll and pager don't react while carrying
                        if (!ch.pressed) break
                        move(d.x, d.y)
                    }
                    end()
                }
            }
    ) {
        WidgetView(pl.type, ui, c, open, Modifier.fillMaxSize())
        if (editing) {
            // Highlight + tap blocker while editing.
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(22.dp))
                    .background(p.accent.copy(alpha = if (isActive) 0.18f else 0.10f))
                    .border(2.dp, p.accent, RoundedCornerShape(22.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
            )
            Text(
                pl.type.title, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Box(
                Modifier.align(Alignment.TopStart).padding(4.dp).size(36.dp).clip(CircleShape).background(p.danger)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(20.dp)) }
            // Proportional resize: only width changes, height follows the widget's fixed aspect ratio.
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(44.dp).clip(CircleShape).background(p.accent)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start() },
                            onDragEnd = { end() },
                            onDragCancel = { end() },
                        ) { change, d ->
                            change.consume()
                            onResize((d.x + d.y / pl.type.aspect) / 2f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.OpenInFull, "Resize", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}
