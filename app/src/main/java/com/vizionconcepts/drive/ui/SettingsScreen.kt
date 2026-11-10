package com.vizionconcepts.drive.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizionconcepts.drive.AppContainer
import com.vizionconcepts.drive.HomeActivity
import com.vizionconcepts.drive.dashboard.WidgetType
import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.obd.ObdState
import com.vizionconcepts.drive.settings.ACCENTS
import com.vizionconcepts.drive.settings.BackgroundStyle
import com.vizionconcepts.drive.settings.DockPosition
import com.vizionconcepts.drive.settings.DialColor
import com.vizionconcepts.drive.settings.GaugeStyle
import com.vizionconcepts.drive.settings.NumberColor
import com.vizionconcepts.drive.settings.LayoutMode
import com.vizionconcepts.drive.settings.Settings
import com.vizionconcepts.drive.settings.SpeedUnit
import com.vizionconcepts.drive.settings.ThemeMode
import kotlinx.coroutines.launch

enum class SettingsTab(val title: String) {
    APPEARANCE("Appearance"), GAUGES("Gauges"), DIGITAL_DASH("Digital Dash"), LAYOUT("Layout & dock"), DRIVING("Driving"),
    OBD("OBD-II"), AMBIENT("Ambient light"), APPS("Apps & startup"), ABOUT("About"),
}

@Composable
fun SettingsScreen(ui: UiState, c: AppContainer, activity: HomeActivity, initial: SettingsTab, onClose: () -> Unit) {
    val p = LocalPalette.current
    var tab by remember { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()
    val update: ((Settings) -> Settings) -> Unit = { t -> scope.launch { c.settings.update(t) } }

    Row(Modifier.fillMaxSize().background(p.background).padding(24.dp)) {
        Column(Modifier.width(240.dp).fillMaxHeight()) {
            Text("Settings", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            for (t in SettingsTab.entries) {
                Text(
                    t.title,
                    color = if (t == tab) p.accent else p.text,
                    fontSize = 18.sp,
                    fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (t == tab) p.card else Color.Transparent)
                        .clickable { tab = t }.padding(14.dp),
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tab.title, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Default.Close, onClick = onClose)
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                SettingsTab.APPEARANCE -> Appearance(ui.settings, update)
                SettingsTab.GAUGES -> Gauges(ui.settings, update)
                SettingsTab.DIGITAL_DASH -> DigitalDashSettings(ui, c, update)
                SettingsTab.LAYOUT -> LayoutSettings(ui.settings, update)
                SettingsTab.DRIVING -> Driving(ui.settings, update)
                SettingsTab.OBD -> ObdSettings(ui, c, activity, update)
                SettingsTab.AMBIENT -> AmbientSettings(ui, c, activity, update)
                SettingsTab.APPS -> AppsSettings(ui, c, update)
                SettingsTab.ABOUT -> About()
            }
        }
    }
}

// ---------- building blocks ----------

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(18.dp)).background(p.card).padding(18.dp),
        content = {
            Text(title.uppercase(), color = p.dim, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        },
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = p.dim, fontSize = 13.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
private fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(title, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = p.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value, onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent))
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp).horizontalScroll(rememberScrollState())) {
        for (o in options) Chip(label(o), selected = o == selected) { onSelect(o) }
    }
}

@Composable
private fun Body(text: String) = Text(text, color = LocalPalette.current.dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

// ---------- tabs ----------

@Composable
private fun Appearance(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val ok = BackgroundImage.save(context, uri)
            busy = false
            if (ok) update { it.copy(background = BackgroundStyle.IMAGE, bgVersion = System.currentTimeMillis()) }
            else android.widget.Toast.makeText(context, "Couldn't open that image", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Section("Theme") {
        ChoiceRow(ThemeMode.entries, s.themeMode, { when (it) { ThemeMode.AUTO -> "Auto (night 19–7)"; ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light" } }) { m ->
            update { it.copy(themeMode = m) }
        }
    }
    Section("Accent colour") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (a in ACCENTS) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(a))
                        .border(if (a == s.accent) 3.dp else 0.dp, p.text, CircleShape)
                        .clickable { update { it.copy(accent = a) } }
                )
            }
        }
        Body("Gauges, highlights and (optionally) your ambient lighting use this colour. Or pick any colour on the wheel:")
        ColorWheel(s.accent, { argb -> update { it.copy(accent = argb) } }, Modifier.padding(top = 8.dp).size(200.dp))
    }
    Section("Background") {
        ChoiceRow(BackgroundStyle.entries, s.background, { it.label }) { b ->
            if (b == BackgroundStyle.IMAGE && s.bgVersion == 0L) picker.launch("image/*") else update { it.copy(background = b) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Chip(if (s.bgVersion > 0) "Change photo" else "Choose photo…") { picker.launch("image/*") }
            if (s.bgVersion > 0) Chip("Remove photo") {
                BackgroundImage.delete(context)
                update { it.copy(bgVersion = 0, background = BackgroundStyle.GRAPHITE) }
            }
        }
        if (busy) Body("Loading photo…")
        if (s.bgVersion > 0) {
            Box(Modifier.fillMaxWidth().height(160.dp).padding(top = 10.dp).clip(RoundedCornerShape(14.dp))) {
                PhotoBackground(s.bgVersion, s.bgDim, s.bgBlur)
                Text("Preview", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            SliderRow("Darken photo", "${(s.bgDim * 100).toInt()}%", s.bgDim, 0f..0.85f) { v -> update { it.copy(bgDim = v) } }
            ToggleRow("Blur photo", "Softens busy photos so gauges pop", s.bgBlur) { on -> update { it.copy(bgBlur = on) } }
            Body("Tip: lower Card opacity below to let more of the photo show through.")
        } else {
            Body("Pick any image from the head unit: gallery, Downloads or a USB stick.")
        }
    }
    Section("Cards") {
        SliderRow("Card opacity", "${(s.cardOpacity * 100).toInt()}%", s.cardOpacity, 0.3f..1f) { v -> update { it.copy(cardOpacity = v) } }
    }
}

@Composable
private fun Gauges(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    val dial = when (s.dialColor) {
        DialColor.ACCENT -> p.accent
        DialColor.CUSTOM -> Color(s.customDial)
        else -> Color(s.dialColor.argb)
    }
    val sampleSpeed = if (s.unit == SpeedUnit.MPH) 62 else 100
    val speedMax = if (s.unit == SpeedUnit.MPH) 160f else 260f
    Section("Speedometer style") {
        Body("Tap a gauge. It's used for the big speed on the vehicle panel and the \"Speedometer (your gauge style)\" widget.")
        GaugeGrid(s.speedStyle, dial) { st -> update { it.copy(speedStyle = st) } }
    }
    Section("Dial colour") {
        Body("Glow, needle, band and lit ticks. Pick a preset or use the wheel for any colour.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())) {
            for (dc in DialColor.entries.filter { it != DialColor.CUSTOM }) {
                val col = if (dc == DialColor.ACCENT) p.accent else Color(dc.argb)
                Swatch(col, dc.label, dc == s.dialColor) { update { it.copy(dialColor = dc) } }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorWheel(if (s.dialColor == DialColor.CUSTOM) s.customDial else (dial.toArgb().toLong() and 0xFFFFFFFFL), { argb ->
                update { it.copy(dialColor = DialColor.CUSTOM, customDial = argb) }
            })
            Spacer(Modifier.width(20.dp))
            Box(Modifier.weight(1f).height(220.dp)) {
                StyledGauge(
                    s.speedStyle, sampleSpeed.toFloat(), speedMax, "$sampleSpeed", s.unit.label, Modifier.fillMaxSize(),
                    numberSize = 72.sp, numberColor = if (s.speedColor == NumberColor.ACCENT) p.accent else null,
                    dialColor = dial, gear = "D",
                )
            }
        }
    }
    Section("Speed number colour") {
        ChoiceRow(NumberColor.entries, s.speedColor, { if (it == NumberColor.TEXT) "White" else "Accent colour" }) { c ->
            update { it.copy(speedColor = c) }
        }
    }
    Section("Tachometer style") {
        Body("For the \"Tachometer (your gauge style)\" widget. Needs engine data (OBD or CANbus).")
        GaugeGrid(s.rpmStyle, dial, rpm = true, redFrom = s.shiftRpm / 1000f) { st -> update { it.copy(rpmStyle = st) } }
        ToggleRow("RPM bar on the vehicle panel", "Shows a shift-light bar under the speed", s.panelRpm) { on ->
            update { it.copy(panelRpm = on) }
        }
    }
}

@Composable
private fun DigitalDashSettings(ui: UiState, c: AppContainer, update: ((Settings) -> Settings) -> Unit) {
    val ctx = LocalContext.current
    val s = ui.settings
    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
    Section("Dash colour") {
        Body("Tacho band, segment meters, sub-dial hands and accents on both dashes. The red shift zone starts at your shift-light rpm (Driving tab).")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())) {
            for (dc in DialColor.entries.filter { it != DialColor.CUSTOM && it != DialColor.ACCENT }) {
                Swatch(Color(dc.argb), dc.label, dc.argb == s.dashColor) { update { it.copy(dashColor = dc.argb) } }
            }
        }
        ColorWheel(s.dashColor, { argb -> update { it.copy(dashColor = argb) } })
    }
    val dashes = listOf(
        WidgetType.RACE_DASH to "Motorsport display: big two-colour tacho with shift zone and warning lights, rpm/speed/time boxes, " +
            "and eight bar-meter tiles – coolant, fuel, outside temp, trip, average speed, top speed, trip time and altitude.",
        WidgetType.TWIN_DASH to "Two large dials: speedometer with rpm, compass and altitude sub-dials, and a watch face " +
            "with date. Digital clock, speed and now-playing in the centre; rainbow rpm and speed bars on the edges; " +
            "coolant, fuel and outside-temp mini gauges.",
    )
    for ((type, about) in dashes) {
        Section(type.title.substringBefore(" –")) {
            Body(about)
            Box(Modifier.fillMaxWidth().aspectRatio(1f / type.aspect).padding(vertical = 6.dp)) {
                if (type == WidgetType.RACE_DASH) RaceDash(ui, Modifier.fillMaxSize()) else TwinDash(ui, Modifier.fillMaxSize())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                Chip("Add as its own page (full size)", selected = true) {
                    update { it.copy(layout = it.layout.addFullPage(type)) }
                    toast("Added as a new home page – swipe to it")
                }
                Chip("Add to current page") {
                    update { st ->
                        val page = c.currentPage.value.coerceIn(0, (st.layout.pages.size - 1).coerceAtLeast(0))
                        st.copy(layout = st.layout.addWidget(page, type))
                    }
                    toast("Added below your widgets – Edit dash to move it")
                }
            }
        }
    }
    Section("Size") {
        Body("Dashes are widgets: Edit dash → drag the corner handle to shrink or grow one. It always keeps its shape; full width is the largest size.")
    }
    Section("Data shown") {
        Body("Only values the head unit can read itself: rpm, speed, coolant temp, fuel level and outside temp from the car's CANbus " +
            "(via the CAN decoder), plus GPS (trip, heading, altitude) and the clock. Anything not received yet shows --. " +
            "Engine-computer-only values such as boost, throttle, AFR and oil temp are left out.")
    }
}

/** Every style as a live mini gauge, 3 per row — nothing hidden off-screen. */
@Composable
private fun GaugeGrid(selected: GaugeStyle, dial: Color, rpm: Boolean = false, redFrom: Float? = null, onSelect: (GaugeStyle) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        for (row in GaugeStyle.entries.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (g in row) {
                    val on = g == selected
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (on) p.accent.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.35f))
                            .border(if (on) 2.dp else 0.dp, p.accent, RoundedCornerShape(16.dp))
                            .clickable { onSelect(g) }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(130.dp)) {
                            if (rpm) {
                                val digital = g == GaugeStyle.DIGITAL || g == GaugeStyle.BAR
                                StyledGauge(g, 4.2f, 8f, if (digital) "4200" else "4.2", if (digital) "rpm" else "x1000",
                                    Modifier.fillMaxSize(), redFrom = redFrom, numberSize = 34.sp, dialColor = dial)
                            } else {
                                StyledGauge(g, 100f, 260f, "100", "km/h", Modifier.fillMaxSize(), numberSize = 34.sp, dialColor = dial)
                            }
                        }
                        Text(g.label, color = if (on) p.accent else p.text, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(col: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(col).border(if (selected) 3.dp else 0.dp, p.text, CircleShape))
        Text(label, color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun LayoutSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    Section("Home layout") {
        ChoiceRow(LayoutMode.entries, s.layoutMode, { if (it == LayoutMode.SPLIT) "Split (vehicle panel + widgets)" else "Widgets only" }) { m ->
            update { it.copy(layoutMode = m) }
        }
        Body("Split mode keeps a large speedometer on the left, like a Tesla-style head unit.")
    }
    Section("Dock") {
        ChoiceRow(DockPosition.entries, s.dockPosition, { if (it == DockPosition.BOTTOM) "Bottom" else "Top" }) { d ->
            update { it.copy(dockPosition = d) }
        }
        Body("Add apps to the dock from the app drawer (long-press an app). Long-press a dock icon to remove it.")
    }
    Section("Note widget") {
        var text by remember(s.noteText) { mutableStateOf(s.noteText) }
        OutlinedTextField(
            text, { text = it; update { st -> st.copy(noteText = it) } },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = p.text, unfocusedTextColor = p.text,
                focusedBorderColor = p.accent, unfocusedBorderColor = p.dim,
            ),
        )
    }
}

@Composable
private fun Driving(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Section("Units") {
        ChoiceRow(SpeedUnit.entries, s.unit, { it.label }) { u -> update { it.copy(unit = u) } }
    }
    Section("Speed alert") {
        SliderRow("Warn above", if (s.speedAlert == 0) "Off" else "${s.speedAlert} ${s.unit.label}", s.speedAlert.toFloat(), 0f..150f, steps = 29) { v ->
            update { it.copy(speedAlert = v.toInt()) }
        }
        Body("Beeps and shows a sign on the vehicle panel. You set the limit – it isn't read from maps.")
    }
    Section("Speed-adaptive volume") {
        ToggleRow("Raise volume with speed", "+1 step every 30 km/h above 40 km/h (max +4)", s.speedVolume) { on ->
            update { it.copy(speedVolume = on) }
        }
    }
    Section("Engine protection (OBD)") {
        SliderRow("Coolant alert", "${s.coolantAlertC} °C", s.coolantAlertC.toFloat(), 90f..120f, steps = 29) { v ->
            update { it.copy(coolantAlertC = v.toInt()) }
        }
        SliderRow("Low battery alert", "%.1f V".format(s.lowVoltageAlert), s.lowVoltageAlert, 11f..12.6f, steps = 15) { v ->
            update { it.copy(lowVoltageAlert = (v * 10).toInt() / 10f) }
        }
        SliderRow("Shift light", "${s.shiftRpm} rpm", s.shiftRpm.toFloat(), 3000f..8000f, steps = 49) { v ->
            update { it.copy(shiftRpm = (v / 100).toInt() * 100) }
        }
    }
}

@Composable
private fun ObdSettings(ui: UiState, c: AppContainer, activity: HomeActivity, update: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    val obd = ui.obd
    var adapters by remember { mutableStateOf(c.obd.pairedAdapters()) }
    Section("Connection") {
        val stateText = when (obd.state) {
            ObdState.CONNECTED -> "Connected to ${obd.adapterName}"
            ObdState.CONNECTING -> "Connecting…"
            ObdState.ERROR -> "Error: ${obd.message}"
            ObdState.DISCONNECTED -> "Not connected"
        }
        Text(stateText, color = if (obd.connected) p.ok else if (obd.state == ObdState.ERROR) p.danger else p.text, fontSize = 18.sp)
        if (obd.connected) {
            Body("${obd.supported.size} supported parameters")
            Chip("Disconnect") { c.obd.disconnect() }
        }
        ToggleRow("Connect automatically", "When the launcher starts", ui.settings.obdAutoConnect) { on -> update { it.copy(obdAutoConnect = on) } }
    }
    Section("Paired adapters") {
        Body("Pair your ELM327 adapter in Android Bluetooth settings first (PIN is usually 1234 or 0000), then pick it here.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Refresh") { activity.requestPermissionsIfNeeded(); adapters = c.obd.pairedAdapters() }
            Chip("Bluetooth settings") {
                runCatching { activity.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) }
            }
        }
        if (adapters.isEmpty()) Body("No paired Bluetooth devices found (or Bluetooth permission not granted).")
        for (a in adapters) {
            val selected = a.address == ui.settings.obdAddress
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    update { it.copy(obdAddress = a.address) }
                    c.obd.connect(a.address)
                }.padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = p.text, fontSize = 17.sp)
                    Text(a.address, color = p.dim, fontSize = 12.sp)
                }
                if (selected) Text("Selected", color = p.accent, fontSize = 13.sp)
            }
        }
    }
    if (obd.connected) {
        Section("Live data") {
            for (pid in ObdPid.entries) {
                val v = obd[pid] ?: continue
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(pid.label, color = p.text, modifier = Modifier.weight(1f))
                    Text("%.1f %s".format(v, pid.unit), color = p.accent)
                }
            }
        }
    }
}

@Composable
private fun AmbientSettings(ui: UiState, c: AppContainer, activity: HomeActivity, update: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    val a = ui.ambient
    Section("Controller") {
        Body("Works with ELK-BLEDOM style Bluetooth LED strip controllers (often sold as \"duoCo Strip\" or \"Lotus Lantern\").")
        Text(
            when {
                a.connectedTo.isNotBlank() -> "Connected"
                a.connecting -> "Connecting…"
                a.error.isNotBlank() -> a.error
                else -> "Not connected"
            },
            color = if (a.connectedTo.isNotBlank()) p.ok else p.text, fontSize = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Chip(if (a.scanning) "Scanning…" else "Scan") { activity.requestPermissionsIfNeeded(); c.ambient.scan() }
            if (a.connectedTo.isNotBlank()) Chip("Disconnect") { c.ambient.disconnect(); update { it.copy(ambientAddress = "") } }
        }
        for (d in a.found) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    c.ambient.stopScan(); c.ambient.connect(d.address); update { it.copy(ambientAddress = d.address) }
                }.padding(10.dp)
            ) {
                Text(d.name, color = p.text, modifier = Modifier.weight(1f))
                Text(d.address, color = p.dim, fontSize = 12.sp)
            }
        }
    }
    Section("Lighting") {
        ToggleRow("Match accent colour", "Lights follow the app's accent colour", ui.settings.ambientMatchAccent) { on ->
            update { it.copy(ambientMatchAccent = on) }
        }
        ToggleRow("Power", "", a.on) { c.ambient.setPower(it) }
        SliderRow("Brightness", "${a.brightness}%", a.brightness.toFloat(), 0f..100f) { c.ambient.setBrightness(it.toInt()) }
        Text("Colour", color = p.text, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        ColorWheel(a.color, { argb ->
            if (ui.settings.ambientMatchAccent) update { it.copy(ambientMatchAccent = false) }
            c.ambient.setColor(argb)
        }, Modifier.padding(vertical = 8.dp).size(200.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            for (i in 0 until 6) Chip("Effect ${i + 1}") { c.ambient.playEffect(i) }
        }
    }
}

@Composable
private fun AppsSettings(ui: UiState, c: AppContainer, update: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Section("Navigation app") {
        val navApps = remember(ui.apps) { c.apps.navigationApps() }
        if (navApps.isEmpty()) Body("No navigation apps installed.")
        for (a in navApps) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { update { it.copy(navPackage = a.packageName) } }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(a.icon, null, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(a.label, color = p.text, modifier = Modifier.weight(1f))
                if (a.packageName == ui.settings.navPackage) Text("Selected", color = p.accent)
            }
        }
    }
    Section("Start after boot") {
        val app = c.apps.findPackage(ui.settings.autostartPackage)
        Body(if (app != null) "${app.label} opens after the head unit boots." else "Choose an app from the app drawer (long-press → Start after boot).")
        if (app != null) Chip("Turn off") { update { it.copy(autostartPackage = "") } }
        SliderRow("Countdown", "${ui.settings.autostartDelaySec} s", ui.settings.autostartDelaySec.toFloat(), 0f..30f, steps = 29) { v ->
            update { it.copy(autostartDelaySec = v.toInt()) }
        }
    }
    Section("Screensaver") {
        SliderRow("Start after", if (ui.settings.screensaverMinutes == 0) "Never" else "${ui.settings.screensaverMinutes} min",
            ui.settings.screensaverMinutes.toFloat(), 0f..30f, steps = 29) { v -> update { it.copy(screensaverMinutes = v.toInt()) } }
        SliderRow("Dim", "${(ui.settings.screensaverDim * 100).toInt()}%", ui.settings.screensaverDim, 0f..0.9f) { v ->
            update { it.copy(screensaverDim = v) }
        }
    }
    Section("Permissions") {
        ToggleRow("Media control", "Notification access lets the media widget show and control music", ui.media.hasAccess) {
            ctx.startActivity(c.media.accessIntent())
        }
        Chip("Make Vizion Drive the default launcher") {
            runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun About() {
    Section("Vizion Drive 0.1.0") {
        Body("An independent car launcher for Android head units by Vizion Concepts.")
        Body("Weather: Open-Meteo.com (CC BY 4.0). OBD-II: SAE J1979 standard PIDs via ELM327.")
        Body("No accounts, no tracking, no analytics. Everything stays on the head unit.")
    }
}
