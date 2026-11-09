package com.vizionconcepts.drive.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizionconcepts.drive.AppContainer
import com.vizionconcepts.drive.system.AppEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(ui: UiState, c: AppContainer, onClose: () -> Unit) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val filtered = ui.apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Box(Modifier.fillMaxSize().background(p.background).padding(24.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.text, unfocusedTextColor = p.text,
                        focusedBorderColor = p.accent, unfocusedBorderColor = p.dim,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                RoundIcon(Icons.Default.Close, onClick = onClose)
            }
            Text("Tap to open · long-press for options", color = p.dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.component }) { app ->
                    Column(
                        Modifier.combinedClickable(
                            onClick = { c.apps.launch(app.component); onClose() },
                            onLongClick = { menuFor = app },
                        ).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(app.icon, app.label, Modifier.size(72.dp))
                        Text(
                            app.label, color = p.text, fontSize = 14.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { app -> AppOptions(app, ui, c) { menuFor = null } }
}

@Composable
private fun AppOptions(app: AppEntry, ui: UiState, c: AppContainer, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = ui.settings
    val inDock = app.component in s.dockApps
    fun save(t: (com.vizionconcepts.drive.settings.Settings) -> com.vizionconcepts.drive.settings.Settings) {
        scope.launch { c.settings.update(t) }; onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                TextButton(onClick = {
                    save { it.copy(dockApps = if (inDock) it.dockApps - app.component else it.dockApps + app.component) }
                }) { Text(if (inDock) "Remove from dock" else "Add to dock") }
                TextButton(onClick = { save { it.copy(navPackage = app.packageName) } }) {
                    Text(if (s.navPackage == app.packageName) "✓ Navigation app" else "Use as navigation app")
                }
                TextButton(onClick = {
                    save { it.copy(autostartPackage = if (s.autostartPackage == app.packageName) "" else app.packageName) }
                }) { Text(if (s.autostartPackage == app.packageName) "✓ Starts after boot (tap to stop)" else "Start after boot") }
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    onDismiss()
                }) { Text("App info") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
