package com.vizionconcepts.drive.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizionconcepts.drive.AppContainer
import com.vizionconcepts.drive.HomeActivity
import com.vizionconcepts.drive.dashboard.DashboardLayout
import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.obd.ObdState
import com.vizionconcepts.drive.settings.DockPosition
import com.vizionconcepts.drive.settings.LayoutMode
import com.vizionconcepts.drive.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Overlay { NONE, DRAWER, SETTINGS, SETTINGS_OBD, SETTINGS_AMBIENT, EDITOR, PERFORMANCE }

@Composable
fun HomeRoot(container: AppContainer, activity: HomeActivity) {
    val c = container
    val settings by c.settings.settings.collectAsState(initial = Settings())
    val gps by c.gps.state.collectAsState()
    val obd by c.obd.state.collectAsState()
    val media by c.media.state.collectAsState()
    val weather by c.weather.state.collectAsState()
    val ambient by c.ambient.state.collectAsState()
    val volume by c.car.volume.collectAsState()
    val apps by c.apps.apps.collectAsState()
    val autostart by c.autostart.collectAsState()
    val ui = UiState(settings, gps, obd, media, weather, ambient, volume, apps)

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    DisposableEffect(activity) {
        activity.onHomePressed = { overlay = Overlay.NONE }
        onDispose { activity.onHomePressed = null }
    }

    DriveTheme(settings) {
        val p = LocalPalette.current
        Box(Modifier.fillMaxSize().background(p.background)) {
            if (settings.background == com.vizionconcepts.drive.settings.BackgroundStyle.IMAGE && settings.bgVersion > 0) {
                PhotoBackground(settings.bgVersion, settings.bgDim, settings.bgBlur)
            }
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                if (settings.dockPosition == DockPosition.TOP) Dock(ui, c) { overlay = it }
                val cfg = LocalConfiguration.current
                val portrait = cfg.screenHeightDp > cfg.screenWidthDp
                if (portrait) {
                    // Tesla-style portrait (e.g. 1200x1576): vehicle strip on top, widget pages below.
                    Column(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                        if (settings.layoutMode == LayoutMode.SPLIT) {
                            VehiclePanelPortrait(ui, Modifier.fillMaxWidth().weight(0.3f)) { overlay = it }
                            Spacer(Modifier.height(12.dp))
                        }
                        Pages(ui, c, Modifier.weight(0.7f).fillMaxWidth()) { overlay = it }
                    }
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                        if (settings.layoutMode == LayoutMode.SPLIT) {
                            VehiclePanel(ui, Modifier.weight(0.36f).fillMaxHeight()) { overlay = it }
                            Spacer(Modifier.width(12.dp))
                        }
                        Pages(ui, c, Modifier.weight(0.64f).fillMaxHeight()) { overlay = it }
                    }
                }
                if (settings.dockPosition == DockPosition.BOTTOM) Dock(ui, c) { overlay = it }
            }

            autostart?.let { a ->
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 24.dp).clip(RoundedCornerShape(50))
                        .background(p.card).padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Opening ${a.label} in ${a.secondsLeft}s", color = p.text, fontSize = 16.sp)
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { c.autostart.value = null }) { Text("Cancel", color = p.accent) }
                }
            }

            when (overlay) {
                Overlay.NONE -> Unit
                Overlay.DRAWER -> AppDrawer(ui, c) { overlay = Overlay.NONE }
                Overlay.SETTINGS -> SettingsScreen(ui, c, activity, SettingsTab.APPEARANCE) { overlay = Overlay.NONE }
                Overlay.SETTINGS_OBD -> SettingsScreen(ui, c, activity, SettingsTab.OBD) { overlay = Overlay.NONE }
                Overlay.SETTINGS_AMBIENT -> SettingsScreen(ui, c, activity, SettingsTab.AMBIENT) { overlay = Overlay.NONE }
                Overlay.EDITOR -> DashboardEditor(ui, c) { overlay = Overlay.NONE }
                Overlay.PERFORMANCE -> PerformanceView(ui) { overlay = Overlay.NONE }
            }

            Screensaver(settings, c)
        }
    }
}

@Composable
private fun VehiclePanel(ui: UiState, modifier: Modifier, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val obd = ui.obd
    val over = ui.settings.speedAlert > 0 && ui.speedShown > ui.settings.speedAlert
    Card(modifier, padding = 22) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    when (obd.state) {
                        ObdState.CONNECTED -> "OBD"; ObdState.CONNECTING -> "OBD…"; else -> "GPS"
                    },
                    if (obd.connected || ui.gps.hasFix) p.ok else p.dim,
                ) { open(Overlay.SETTINGS_OBD) }
                Spacer(Modifier.weight(1f))
                if (ui.settings.speedAlert > 0) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(p.danger), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Text("${ui.settings.speedAlert}", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            StyledGauge(
                ui.settings.speedStyle, ui.speedShown.toFloat(), ui.speedMax, "${ui.speedShown}", ui.settings.unit.label,
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                redFrom = ui.settings.speedAlert.takeIf { it > 0 }?.toFloat(),
                numberSize = 140.sp,
                numberColor = if (over) p.danger else ui.speedNumberColor(), dialColor = ui.dialColor(),
            )
            if (obd.connected && ui.settings.panelRpm) {
                val rpm = obd[ObdPid.RPM] ?: 0f
                Column {
                    Text("${rpm.toInt()} rpm", color = p.dim, fontSize = 13.sp)
                    SegmentBar(rpm / 8000f, redFrom = ui.settings.shiftRpm / 8000f, shiftLight = true)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Coolant", obd[ObdPid.COOLANT]?.let { "${it.toInt()}°" } ?: "–",
                    warn = (obd[ObdPid.COOLANT] ?: 0f) >= ui.settings.coolantAlertC)
                MiniStat("Battery", obd[ObdPid.VOLTAGE]?.let { "%.1fV".format(it) } ?: "–")
                MiniStat("Outside", (obd[ObdPid.AMBIENT]?.toInt() ?: ui.weather.tempC.toInt().takeIf { ui.weather.loaded })?.let { "$it°" } ?: "–")
                MiniStat("Trip", "%.0f %s".format(ui.kmShown(ui.gps.tripKm), ui.distanceUnit))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Performance") { open(Overlay.PERFORMANCE) }
                Chip("Edit dash") { open(Overlay.EDITOR) }
            }
        }
    }
}

/** Portrait vehicle strip: huge speed on the left, engine data and shortcuts on the right. */
@Composable
private fun VehiclePanelPortrait(ui: UiState, modifier: Modifier, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val obd = ui.obd
    val over = ui.settings.speedAlert > 0 && ui.speedShown > ui.settings.speedAlert
    Card(modifier, padding = 20) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            StyledGauge(
                ui.settings.speedStyle, ui.speedShown.toFloat(), ui.speedMax, "${ui.speedShown}", ui.settings.unit.label,
                Modifier.weight(0.42f).fillMaxHeight(),
                redFrom = ui.settings.speedAlert.takeIf { it > 0 }?.toFloat(),
                numberSize = 120.sp,
                numberColor = if (over) p.danger else ui.speedNumberColor(), dialColor = ui.dialColor(),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        when (obd.state) { ObdState.CONNECTED -> "OBD"; ObdState.CONNECTING -> "OBD…"; else -> "GPS" },
                        if (obd.connected || ui.gps.hasFix) p.ok else p.dim,
                    ) { open(Overlay.SETTINGS_OBD) }
                    Spacer(Modifier.weight(1f))
                    if (ui.settings.speedAlert > 0) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(p.danger), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Text("${ui.settings.speedAlert}", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (obd.connected && ui.settings.panelRpm) {
                    val rpm = obd[ObdPid.RPM] ?: 0f
                    Column {
                        Text("${rpm.toInt()} rpm", color = p.dim, fontSize = 13.sp)
                        SegmentBar(rpm / 8000f, redFrom = ui.settings.shiftRpm / 8000f, shiftLight = true)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("Coolant", obd[ObdPid.COOLANT]?.let { "${it.toInt()}°" } ?: "–",
                        warn = (obd[ObdPid.COOLANT] ?: 0f) >= ui.settings.coolantAlertC)
                    MiniStat("Battery", obd[ObdPid.VOLTAGE]?.let { "%.1fV".format(it) } ?: "–")
                    MiniStat("Outside", (obd[ObdPid.AMBIENT]?.toInt() ?: ui.weather.tempC.toInt().takeIf { ui.weather.loaded })?.let { "$it°" } ?: "–")
                    MiniStat("Trip", "%.0f %s".format(ui.kmShown(ui.gps.tripKm), ui.distanceUnit))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Performance") { open(Overlay.PERFORMANCE) }
                    Chip("Edit dash") { open(Overlay.EDITOR) }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.dim.copy(alpha = 0.15f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniStat(label: String, value: String, warn: Boolean = false) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = if (warn) p.danger else p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = p.dim, fontSize = 11.sp)
    }
}

@Composable
private fun Pages(ui: UiState, c: AppContainer, modifier: Modifier, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val pages = ui.settings.layout.pages
    val pager = rememberPagerState { pages.size.coerceAtLeast(1) }
    Column(modifier) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
            val widgets = pages.getOrElse(index) { emptyList() }
            if (widgets.isEmpty()) {
                Card(Modifier.fillMaxSize().clickable { open(Overlay.EDITOR) }) {
                    Text("Empty page – tap to add widgets", color = p.dim, modifier = Modifier.align(Alignment.Center))
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (row in DashboardLayout.rows(widgets)) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (w in row) WidgetView(w, ui, c, open, Modifier.weight(w.span.toFloat()).fillMaxHeight())
                            val used = row.sumOf { it.span }
                            if (used < 2) Spacer(Modifier.weight((2 - used).toFloat()))
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) p.accent else p.dim.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(ui: UiState, c: AppContainer, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(5_000) } }
    Row(
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(26.dp)).background(p.card).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(Icons.Default.Apps, "Apps") { open(Overlay.DRAWER) }
        Spacer(Modifier.width(6.dp))
        val cfg = LocalConfiguration.current
        val maxApps = if (cfg.screenHeightDp > cfg.screenWidthDp) 4 else 8
        val dockApps = ui.settings.dockApps.mapNotNull { comp -> ui.apps.firstOrNull { it.component == comp } }.take(maxApps)
        for (a in dockApps) {
            Image(
                a.icon, a.label,
                Modifier.padding(horizontal = 6.dp).size(58.dp).clip(RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { c.apps.launch(a.component) },
                        onLongClick = {
                            scope.launch { c.settings.update { s -> s.copy(dockApps = s.dockApps - a.component) } }
                            android.widget.Toast.makeText(ctx, "${a.label} removed from dock", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
        if (ui.media.active) {
            DockButton(if (ui.media.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause") { c.media.playPause() }
        }
        DockButton(Icons.Default.Navigation, "Navigate") {
            val pkg = ui.settings.navPackage
            if (pkg.isBlank() || !c.apps.launchPackage(pkg)) open(Overlay.SETTINGS)
        }
        DockButton(Icons.Default.Mic, "Voice") { c.car.voiceAssistant() }
        DockButton(Icons.Default.Speed, "Performance") { open(Overlay.PERFORMANCE) }
        DockButton(Icons.Default.Dashboard, "Edit dashboard") { open(Overlay.EDITOR) }
        DockButton(Icons.Default.Settings, "Settings") { open(Overlay.SETTINGS) }
        Spacer(Modifier.width(12.dp))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.padding(horizontal = 4.dp).size(60.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = p.text, modifier = Modifier.size(32.dp)) }
}

/** Full-screen engine view. */
@Composable
fun PerformanceView(ui: UiState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val obd = ui.obd
    var maxRpm by remember { mutableStateOf(0f) }
    var maxBoost by remember { mutableStateOf(0f) }
    val rpm = obd[ObdPid.RPM] ?: 0f
    val map = obd[ObdPid.MAP]
    LaunchedEffect(rpm, map) {
        if (rpm > maxRpm) maxRpm = rpm
        if ((map ?: 0f) > maxBoost) maxBoost = map ?: 0f
    }
    Box(Modifier.fillMaxSize().background(Color.Black).padding(28.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PERFORMANCE", color = p.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Default.Close, onClick = onClose)
            }
            if (!obd.connected) {
                Text("Connect an OBD-II adapter for live engine data. GPS speed shown below.", color = p.dim, fontSize = 16.sp)
            }
            SegmentBar(rpm / 8000f, segments = 48, redFrom = ui.settings.shiftRpm / 8000f, shiftLight = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ui.speedShown}", color = Color.White, fontSize = 150.sp, fontWeight = FontWeight.Bold, lineHeight = 150.sp)
                    Text(ui.settings.unit.label, color = p.dim, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${rpm.toInt()}", color = if (rpm >= ui.settings.shiftRpm) p.danger else Color.White, fontSize = 90.sp, fontWeight = FontWeight.Bold)
                    Text("rpm", color = p.dim, fontSize = 18.sp)
                }
            }
            val stats = listOf(
                "Throttle" to obd[ObdPid.THROTTLE]?.let { "${it.toInt()}%" },
                "Load" to obd[ObdPid.ENGINE_LOAD]?.let { "${it.toInt()}%" },
                "MAP" to map?.let { "${it.toInt()} kPa" },
                "Coolant" to obd[ObdPid.COOLANT]?.let { "${it.toInt()}°C" },
                "Intake" to obd[ObdPid.INTAKE_TEMP]?.let { "${it.toInt()}°C" },
                "Peak RPM" to (if (maxRpm > 0) "${maxRpm.toInt()}" else null),
                "Peak MAP" to (if (maxBoost > 0) "${maxBoost.toInt()} kPa" else null),
                "Top speed" to "${ui.mpsShown(ui.gps.tripMaxMps)} ${ui.settings.unit.label}",
            )
            val cfg = LocalConfiguration.current
            val perRow = if (cfg.screenHeightDp > cfg.screenWidthDp) 4 else 8
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                for (chunk in stats.chunked(perRow)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for ((label, value) in chunk) PerfStat(label, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun PerfStat(label: String, value: String?) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: "–", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(label.uppercase(), color = p.dim, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

/** Idle clock screensaver. Any touch dismisses it (touches reset lastInteraction). */
@Composable
private fun Screensaver(settings: Settings, c: AppContainer) {
    val last by c.lastInteraction.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(5_000) } }
    val show = settings.screensaverMinutes > 0 && now - last > settings.screensaverMinutes * 60_000L
    AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .clickable { c.lastInteraction.value = System.currentTimeMillis(); now = System.currentTimeMillis() },
            contentAlignment = Alignment.Center,
        ) {
            val alpha = 1f - settings.screensaverDim.coerceIn(0f, 0.9f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
                    color = Color.White.copy(alpha = alpha), fontSize = 160.sp, fontWeight = FontWeight.Thin)
                Text(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now)),
                    color = Color.White.copy(alpha = alpha * 0.7f), fontSize = 24.sp)
            }
        }
    }
}
