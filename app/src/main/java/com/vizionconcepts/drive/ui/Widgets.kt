package com.vizionconcepts.drive.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizionconcepts.drive.AppContainer
import com.vizionconcepts.drive.ambient.AmbientState
import com.vizionconcepts.drive.dashboard.WidgetType
import com.vizionconcepts.drive.media.NowPlaying
import com.vizionconcepts.drive.obd.ElmParser
import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.obd.ObdSnapshot
import com.vizionconcepts.drive.settings.Settings
import com.vizionconcepts.drive.system.AppEntry
import com.vizionconcepts.drive.vehicle.GpsState
import com.vizionconcepts.drive.vehicle.TripComputer
import com.vizionconcepts.drive.weather.WeatherNow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the dashboard renders, in one immutable snapshot. */
data class UiState(
    val settings: Settings,
    val gps: GpsState,
    val obd: ObdSnapshot,
    val media: NowPlaying,
    val weather: WeatherNow,
    val ambient: AmbientState,
    val volume: Float,
    val apps: List<AppEntry>,
) {
    /** Vehicle speed in km/h — OBD when available (more accurate), otherwise GPS. */
    val speedKmh: Float get() = obd[ObdPid.SPEED] ?: (gps.speedMps * 3.6f)
    val speedShown: Int get() = (speedKmh / 3.6f * settings.unit.fromMps).toInt()
    val speedMax: Float get() = if (settings.unit.name == "MPH") 160f else 260f

    @Composable
    fun dialColor(): Color = when (settings.dialColor) {
        com.vizionconcepts.drive.settings.DialColor.ACCENT -> LocalPalette.current.accent
        com.vizionconcepts.drive.settings.DialColor.CUSTOM -> Color(settings.customDial)
        else -> Color(settings.dialColor.argb)
    }

    @Composable
    fun speedNumberColor(): Color? =
        if (settings.speedColor == com.vizionconcepts.drive.settings.NumberColor.ACCENT) LocalPalette.current.accent else null
    fun mpsShown(mps: Float): Int = (mps * settings.unit.fromMps).toInt()
    val distanceUnit get() = if (settings.unit.name == "MPH") "mi" else "km"
    fun kmShown(km: Double) = if (settings.unit.name == "MPH") km / 1.609344 else km
}

@Composable
fun WidgetView(type: WidgetType, ui: UiState, c: AppContainer, open: (Overlay) -> Unit, modifier: Modifier = Modifier) {
    // Digital dashes draw their own full-bleed face.
    when (type) {
        WidgetType.RACE_DASH -> { RaceDash(ui, modifier); return }
        WidgetType.TWIN_DASH -> { TwinDash(ui, modifier); return }
        else -> Unit
    }
    Card(modifier) {
        if (type.needsObd && !ui.obd.connected) {
            ObdNeeded(type, onClick = { open(Overlay.SETTINGS_OBD) })
            return@Card
        }
        when (type) {
            WidgetType.SPEEDO -> StyledGauge(
                ui.settings.speedStyle, ui.speedShown.toFloat(), ui.speedMax, "${ui.speedShown}", ui.settings.unit.label,
                Modifier.fillMaxSize(), redFrom = ui.settings.speedAlert.takeIf { it > 0 }?.toFloat(),
                numberColor = ui.speedNumberColor(), dialColor = ui.dialColor(),
            )
            WidgetType.TACHO -> {
                val rpm = ui.obd[ObdPid.RPM] ?: 0f
                StyledGauge(
                    ui.settings.rpmStyle, rpm / 1000f, 8f, if (ui.settings.rpmStyle == com.vizionconcepts.drive.settings.GaugeStyle.DIGITAL || ui.settings.rpmStyle == com.vizionconcepts.drive.settings.GaugeStyle.BAR) "${rpm.toInt()}" else "%.1f".format(rpm / 1000f),
                    if (ui.settings.rpmStyle == com.vizionconcepts.drive.settings.GaugeStyle.DIGITAL || ui.settings.rpmStyle == com.vizionconcepts.drive.settings.GaugeStyle.BAR) "rpm" else "×1000 rpm",
                    Modifier.fillMaxSize(), redFrom = ui.settings.shiftRpm / 1000f, dialColor = ui.dialColor(),
                )
            }
            WidgetType.SPEED_ARC -> ArcGauge(ui.speedShown.toFloat(), 0f, if (ui.settings.unit.name == "MPH") 140f else 220f,
                "${ui.speedShown}", ui.settings.unit.label, Modifier.fillMaxSize())
            WidgetType.SPEED_DIGITAL -> BigNumber("${ui.speedShown}", ui.settings.unit.label, if (ui.gps.hasFix || ui.obd.connected) "" else "waiting for GPS")
            WidgetType.SPEED_BAR -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                BigNumber("${ui.speedShown}", ui.settings.unit.label, "")
                SegmentBar(ui.speedKmh / 200f, redFrom = 0.9f)
            }
            WidgetType.RPM_ARC -> {
                val rpm = ui.obd[ObdPid.RPM] ?: 0f
                ArcGauge(rpm / 1000f, 0f, 8f, "%.1f".format(rpm / 1000f), "×1000 rpm", Modifier.fillMaxSize(), redFrom = ui.settings.shiftRpm / 1000f)
            }
            WidgetType.RPM_BAR -> {
                val rpm = ui.obd[ObdPid.RPM] ?: 0f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    BigNumber("${rpm.toInt()}", "rpm", "")
                    SegmentBar(rpm / 8000f, redFrom = ui.settings.shiftRpm / 8000f, shiftLight = true)
                }
            }
            WidgetType.OBD_SINGLE -> {
                val v = ui.obd[ObdPid.COOLANT]
                ArcGauge(v ?: 0f, 40f, 130f, v?.toInt()?.toString() ?: "–", "Coolant °C", Modifier.fillMaxSize(), redFrom = ui.settings.coolantAlertC.toFloat(), majorTicks = 6)
            }
            WidgetType.OBD_QUAD -> ObdQuad(ui.obd)
            WidgetType.CLOCK -> ClockWidget()
            WidgetType.WEATHER -> WeatherWidget(ui.weather)
            WidgetType.COMPASS -> CompassWidget(ui.gps)
            WidgetType.MEDIA -> MediaWidget(ui.media, c)
            WidgetType.NAVIGATION -> NavWidget(ui, c, open)
            WidgetType.APP_SHORTCUTS -> AppShortcuts(ui, c, open)
            WidgetType.TRIP -> TripWidget(ui, c)
            WidgetType.DTC -> DtcWidget(ui.obd, c)
            WidgetType.AMBIENT -> AmbientWidget(ui.ambient, c, open)
            WidgetType.VOLUME -> VolumeWidget(ui.volume, c)
            WidgetType.NOTE -> NoteWidget(ui.settings.noteText)
            WidgetType.RACE_DASH, WidgetType.TWIN_DASH -> Unit
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text.uppercase(), color = LocalPalette.current.dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

@Composable
fun BigNumber(value: String, unit: String, hint: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = p.text, fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp)
        Text(unit, color = p.dim, fontSize = 14.sp)
        if (hint.isNotEmpty()) Text(hint, color = p.warn, fontSize = 12.sp)
    }
}

@Composable
private fun ObdNeeded(type: WidgetType, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().clickable(onClick = onClick), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Title(type.title)
        Spacer(Modifier.height(8.dp))
        Text("Connect OBD-II adapter", color = p.text, fontSize = 16.sp)
        Text("Tap to set up", color = p.accent, fontSize = 13.sp)
    }
}

@Composable
private fun ObdQuad(obd: ObdSnapshot) {
    val p = LocalPalette.current
    val pids = listOf(ObdPid.COOLANT, ObdPid.VOLTAGE, ObdPid.ENGINE_LOAD, ObdPid.INTAKE_TEMP)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        for (row in pids.chunked(2)) {
            Row(Modifier.fillMaxWidth()) {
                for (pid in row) {
                    val v = obd[pid]
                    Column(Modifier.weight(1f).padding(6.dp)) {
                        Title(pid.label)
                        Text(
                            v?.let { if (pid == ObdPid.VOLTAGE) "%.1f".format(it) else it.toInt().toString() } ?: "–",
                            color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(pid.unit, color = p.dim, fontSize = 12.sp)
                        ThinBar(((v ?: pid.min) - pid.min) / (pid.max - pid.min), p.accent)
                    }
                }
            }
        }
    }
}

@Composable
fun ClockWidget() {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now))
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = p.text, fontSize = 60.sp, fontWeight = FontWeight.Light)
        Text(date, color = p.dim, fontSize = 16.sp)
    }
}

@Composable
private fun WeatherWidget(w: WeatherNow) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Title("Weather")
        if (!w.loaded) {
            Text(if (w.error.isNotEmpty()) w.error else "Waiting for location", color = p.dim, fontSize = 14.sp)
        } else {
            Text("${w.icon}  ${w.tempC.toInt()}°", color = p.text, fontSize = 48.sp, fontWeight = FontWeight.Light)
            Text("${w.description} · wind ${w.windKmh.toInt()} km/h", color = p.dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CompassWidget(g: GpsState) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        CompassRose(g.bearing, Modifier.weight(1f).fillMaxSize())
        Column(Modifier.padding(start = 8.dp)) {
            Text(TripComputer.cardinal(g.bearing), color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("${g.bearing.toInt()}°", color = p.dim, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Title("Altitude")
            Text(if (g.hasFix) "${g.altitudeM.toInt()} m" else "–", color = p.text, fontSize = 18.sp)
        }
    }
}

@Composable
private fun MediaWidget(m: NowPlaying, c: AppContainer) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    if (!m.hasAccess) {
        Column(Modifier.fillMaxSize().clickable { ctx.startActivity(c.media.accessIntent()) },
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MusicNote, null, tint = p.accent, modifier = Modifier.size(36.dp))
            Text("Allow notification access", color = p.text, fontSize = 16.sp)
            Text("so Vizion Drive can show and control your music", color = p.dim, fontSize = 12.sp)
        }
        return
    }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(110.dp).clip(RoundedCornerShape(16.dp)).background(p.dim.copy(alpha = 0.2f))
                .clickable { if (m.appPackage.isNotEmpty()) c.apps.launchPackage(m.appPackage) },
            contentAlignment = Alignment.Center,
        ) {
            val art = m.art
            if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, null, tint = p.dim, modifier = Modifier.size(42.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(m.title.ifEmpty { "Nothing playing" }, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.artist, color = p.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            if (m.durationMs > 0) ThinBar(m.positionMs.toFloat() / m.durationMs, p.accent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Default.SkipPrevious) { c.media.previous() }
                RoundIcon(if (m.playing) Icons.Default.Pause else Icons.Default.PlayArrow, big = true) { c.media.playPause() }
                RoundIcon(Icons.Default.SkipNext) { c.media.next() }
            }
        }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, big: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    IconButton(onClick = onClick, modifier = Modifier.size(if (big) 60.dp else 48.dp)) {
        Icon(icon, null, tint = tint ?: p.text, modifier = Modifier.size(if (big) 40.dp else 30.dp))
    }
}

@Composable
private fun NavWidget(ui: UiState, c: AppContainer, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val navApp = ui.apps.firstOrNull { it.packageName == ui.settings.navPackage }
    fun go(query: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (navApp != null) i.setPackage(navApp.packageName)
        runCatching { ctx.startActivity(i) }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
            if (navApp != null) c.apps.launchPackage(navApp.packageName) else open(Overlay.SETTINGS)
        }) {
            Icon(Icons.Default.Navigation, null, tint = p.accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(navApp?.label ?: "Choose nav app", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(if (navApp != null) "Tap to open" else "Settings → Navigation", color = p.dim, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (q in listOf("Fuel", "Food", "Parking")) Chip(q) { go(q) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) p.accent else p.dim.copy(alpha = 0.18f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = if (selected) Color.White else p.text, fontSize = 14.sp) }
}

@Composable
private fun AppShortcuts(ui: UiState, c: AppContainer, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val apps = ui.settings.dockApps.mapNotNull { comp -> ui.apps.firstOrNull { it.component == comp } }.take(6)
    if (apps.isEmpty()) {
        Column(Modifier.fillMaxSize().clickable { open(Overlay.DRAWER) }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No dock apps yet", color = p.text)
            Text("Open the app drawer and long-press an app", color = p.dim, fontSize = 12.sp)
        }
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for (a in apps) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { c.apps.launch(a.component) }) {
                Image(a.icon, a.label, Modifier.size(64.dp))
                Text(a.label, color = p.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(80.dp))
            }
        }
    }
}

@Composable
private fun TripWidget(ui: UiState, c: AppContainer) {
    val p = LocalPalette.current
    val g = ui.gps
    val mins = g.tripMovingMs / 60_000
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Title("Trip")
            TextButton(onClick = { c.gps.resetTrip() }) { Text("Reset", color = p.accent) }
        }
        Text("%.1f %s".format(ui.kmShown(g.tripKm), ui.distanceUnit), color = p.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Time", "${mins / 60}:%02d".format(mins % 60))
            Stat("Avg", "${ui.mpsShown(g.tripAvgMps)}")
            Stat("Max", "${ui.mpsShown(g.tripMaxMps)}")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val p = LocalPalette.current
    Column {
        Text(label, color = p.dim, fontSize = 11.sp)
        Text(value, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DtcWidget(obd: ObdSnapshot, c: AppContainer) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Title("Engine codes")
        val dtcs = obd.dtcs
        when {
            obd.dtcBusy -> Text("Talking to the car…", color = p.dim)
            dtcs == null -> Text("Not scanned", color = p.dim)
            dtcs.isEmpty() -> Text("All clear", color = p.ok, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            else -> Column {
                Text("${dtcs.size} code(s)", color = p.danger, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                for (code in dtcs.take(3)) Text("$code  ${ElmParser.systemName(code)}", color = p.text, fontSize = 14.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Scan") { c.obd.scanDtcs() }
            if (!obd.dtcs.isNullOrEmpty()) Chip("Clear") {
                c.obd.clearDtcs { ok ->
                    android.widget.Toast.makeText(ctx, if (ok) "Codes cleared" else "Car refused clear (engine running?)", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
private fun AmbientWidget(a: AmbientState, c: AppContainer, open: (Overlay) -> Unit) {
    val p = LocalPalette.current
    val connected = a.connectedTo.isNotBlank()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lightbulb, null, tint = if (connected && a.on) Color(a.color) else p.dim, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Title("Ambient light")
            Spacer(Modifier.weight(1f))
            if (connected) RoundIcon(Icons.Default.PowerSettingsNew, tint = if (a.on) p.accent else p.dim) { c.ambient.setPower(!a.on) }
        }
        if (!connected) {
            Text(if (a.connecting) "Connecting…" else "Not connected", color = p.dim)
            Chip("Set up") { open(Overlay.SETTINGS_AMBIENT) }
        } else {
            Slider(
                value = a.brightness / 100f, onValueChange = { c.ambient.setBrightness((it * 100).toInt()) },
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in com.vizionconcepts.drive.settings.ACCENTS.take(6)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(col)).clickable { c.ambient.setColor(col) })
                }
            }
        }
    }
}

@Composable
private fun VolumeWidget(volume: Float, c: AppContainer) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Title("Volume")
        Text("${(volume * 100).toInt()}%", color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeDown, null, tint = p.dim)
            Slider(volume, { c.car.setVolumeFraction(it) }, Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent))
            Icon(Icons.Default.VolumeUp, null, tint = p.dim)
        }
    }
}

@Composable
private fun NoteWidget(text: String) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}
