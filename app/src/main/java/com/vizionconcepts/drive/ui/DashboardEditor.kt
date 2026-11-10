package com.vizionconcepts.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizionconcepts.drive.AppContainer
import com.vizionconcepts.drive.dashboard.DashboardLayout
import com.vizionconcepts.drive.dashboard.Presets
import com.vizionconcepts.drive.dashboard.WidgetType
import kotlinx.coroutines.launch

@Composable
fun DashboardEditor(ui: UiState, c: AppContainer, onClose: () -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val layout = ui.settings.layout
    var page by remember { mutableIntStateOf(c.currentPage.value) }
    val current = page.coerceIn(0, layout.pages.lastIndex.coerceAtLeast(0))
    fun save(l: DashboardLayout) = scope.launch { c.settings.update { it.copy(layout = l) } }

    Box(Modifier.fillMaxSize().background(p.background).padding(24.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Widgets, pages & presets", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Default.Close, onClick = onClose)
            }
            Spacer(Modifier.padding(6.dp))
            // Presets
            Text("PRESETS", color = p.dim, fontSize = 12.sp, letterSpacing = 1.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(Presets.entries.toList()) { preset ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(p.card)
                            .clickable { save(preset.layout); page = 0 }.padding(14.dp)
                    ) {
                        Text(preset.title, color = p.text, fontWeight = FontWeight.SemiBold)
                        Text(preset.description, color = p.dim, fontSize = 12.sp)
                    }
                }
            }
            // Pages
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                for (i in layout.pages.indices) {
                    Chip("Page ${i + 1}", selected = i == current) { page = i }
                    Spacer(Modifier.width(6.dp))
                }
                RoundIcon(Icons.Default.Add) { save(layout.addPage()); page = layout.pages.size }
                if (layout.pages.size > 1) {
                    TextButton(onClick = { save(layout.removePage(current)); page = (current - 1).coerceAtLeast(0) }) {
                        Text("Remove page", color = p.danger)
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Widgets on this page
                Card(Modifier.weight(1f).fillMaxHeight()) {
                    val widgets = layout.types(current)
                    if (widgets.isEmpty()) Text("No widgets yet – add some from the right", color = p.dim)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(widgets) { i, w ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}. ${w.title}", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                if (w.span == 2) Text("wide", color = p.dim, fontSize = 11.sp)
                                RoundIcon(Icons.Default.ArrowUpward) { save(layout.moveWidget(current, i, i - 1)) }
                                RoundIcon(Icons.Default.ArrowDownward) { save(layout.moveWidget(current, i, i + 1)) }
                                RoundIcon(Icons.Default.Delete, tint = p.danger) { save(layout.removeWidget(current, i)) }
                            }
                        }
                    }
                }
                // Catalogue
                Card(Modifier.weight(1f).fillMaxHeight()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item { Text("ADD WIDGET", color = p.dim, fontSize = 12.sp, letterSpacing = 1.sp) }
                        item { Text("Digital dashes live in Settings → Digital Dash", color = p.accent, fontSize = 13.sp, modifier = Modifier.padding(10.dp)) }
                        items(WidgetType.entries.filter { !it.isFullDash }) { w ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (layout.pages.isEmpty()) save(DashboardLayout.of(listOf(w))) else save(layout.addWidget(current, w))
                                        android.widget.Toast.makeText(ctx, "${w.title} added – drag it into place", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(w.title, color = p.text, modifier = Modifier.weight(1f))
                                if (w.needsObd) Text("OBD", color = p.accent, fontSize = 11.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (w.span == 2) "2 col" else "1 col", color = p.dim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
