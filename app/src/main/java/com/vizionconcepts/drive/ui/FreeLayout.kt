package com.vizionconcepts.drive.ui

import androidx.compose.foundation.background
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
 * Swipeable pages of freely placed widgets. In edit mode every widget can be dragged
 * anywhere and resized from its corner handle; size changes keep the widget's proportions.
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
                    Text("Drag to move · drag the corner handle to resize · ✕ to remove", color = p.dim, fontSize = 12.sp)
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
                val contentH = wDp * (items.maxOf { it.bottom } + 0.02f)
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(if (editing) contentH + 200.dp else contentH)) {
                        items.forEachIndexed { i, pl ->
                            PlacedWidget(
                                pl, wDp, wPx, editing, ui, c, open,
                                // Edited widget comes to the front so its handles are never hidden under another widget.
                                onCommit = { np -> commit(draft.update(index, i, np).moveWidget(index, i, items.lastIndex)) },
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
    wDp: Dp,
    wPx: Float,
    editing: Boolean,
    ui: UiState,
    c: AppContainer,
    open: (Overlay) -> Unit,
    onCommit: (Placement) -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    var live by remember(pl) { mutableStateOf(pl) }
    Box(
        Modifier
            .offset(x = wDp * live.x, y = wDp * live.y)
            .size(wDp * live.w, wDp * live.h)
            .padding(6.dp)
    ) {
        WidgetView(pl.type, ui, c, open, Modifier.fillMaxSize())
        if (editing) {
            // Drag surface over the whole widget (also blocks taps on the widget while editing).
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(22.dp))
                    .background(p.accent.copy(alpha = 0.10f))
                    .border(2.dp, p.accent, RoundedCornerShape(22.dp))
                    .pointerInput(pl) {
                        detectDragGestures(onDragEnd = { onCommit(live.moved(live.x, live.y)) }) { change, d ->
                            change.consume()
                            live = live.copy(
                                x = (live.x + d.x / wPx).coerceIn(0f, 1f - live.w),
                                y = (live.y + d.y / wPx).coerceAtLeast(0f),
                            )
                        }
                    }
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
                    .pointerInput(pl) {
                        detectDragGestures(onDragEnd = { onCommit(live.resized(live.w)) }) { change, d ->
                            change.consume()
                            val grow = (d.x + d.y / live.type.aspect) / 2f / wPx
                            live = live.copy(w = (live.w + grow).coerceIn(Placement.MIN_W, 1f - live.x))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.OpenInFull, "Resize", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}
