package com.vizionconcepts.drive.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vizionconcepts.drive.dashboard.DashboardLayout
import com.vizionconcepts.drive.dashboard.Presets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { AUTO, DARK, LIGHT }
enum class SpeedUnit(val label: String, val fromMps: Float) {
    KMH("km/h", 3.6f), MPH("mph", 2.23694f)
}
enum class LayoutMode { SPLIT, FULL }
enum class GaugeStyle(val label: String) {
    SPORT_GLOW("Sport glow"), CHROME("Chrome classic"), RACE_BAND("Race band"), ROAD("Road view"),
    DIGITAL("Digital"), ARC("Arc"), NEEDLE("Needle dial"), RING("Ring"), BAR("Segment bar"), HUD("HUD")
}
enum class DialColor(val label: String, val argb: Long) {
    ACCENT("Accent", 0), RED("Red", 0xFFFF2D1F), CYAN("Cyan", 0xFF22D3EE), AMBER("Amber", 0xFFFFB020),
    WHITE("White", 0xFFF4F6F8), GREEN("Green", 0xFF34C759), PURPLE("Purple", 0xFFBF5AF2)
}
enum class NumberColor { TEXT, ACCENT }
enum class DockPosition { BOTTOM, TOP }
enum class BackgroundStyle(val label: String) {
    BLACK("Pure black"), GRAPHITE("Graphite"), MIDNIGHT("Midnight blue"),
    CARBON("Carbon weave"), EMBER("Ember glow")
}

/** Accent palette offered in settings (ARGB). */
val ACCENTS: List<Long> = listOf(
    0xFFFF3B30, 0xFFFF9500, 0xFFFFD60A, 0xFF34C759,
    0xFF00C7BE, 0xFF0A84FF, 0xFFBF5AF2, 0xFFE8EDF2,
)

data class Settings(
    val accent: Long = ACCENTS[0],
    val themeMode: ThemeMode = ThemeMode.DARK,
    val background: BackgroundStyle = BackgroundStyle.GRAPHITE,
    val cardOpacity: Float = 0.85f,
    val unit: SpeedUnit = SpeedUnit.KMH,
    val speedAlert: Int = 0,
    val speedVolume: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.SPLIT,
    val dockPosition: DockPosition = DockPosition.BOTTOM,
    val dockApps: List<String> = emptyList(),
    val layout: DashboardLayout = Presets.CLASSIC.layout,
    val navPackage: String = "",
    val obdAddress: String = "",
    val obdAutoConnect: Boolean = true,
    val ambientAddress: String = "",
    val ambientMatchAccent: Boolean = true,
    val screensaverMinutes: Int = 0,
    val screensaverDim: Float = 0.6f,
    val autostartPackage: String = "",
    val autostartDelaySec: Int = 5,
    val coolantAlertC: Int = 105,
    val lowVoltageAlert: Float = 11.8f,
    val shiftRpm: Int = 6000,
    val noteText: String = "Vizion Drive",
    val speedStyle: GaugeStyle = GaugeStyle.SPORT_GLOW,
    val dialColor: DialColor = DialColor.RED,
    val rpmStyle: GaugeStyle = GaugeStyle.BAR,
    val speedColor: NumberColor = NumberColor.TEXT,
    val panelRpm: Boolean = true,
)

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "vizion_drive")

class SettingsRepository(private val context: Context) {

    private object K {
        val accent = longPreferencesKey("accent")
        val themeMode = stringPreferencesKey("theme_mode")
        val background = stringPreferencesKey("background")
        val cardOpacity = floatPreferencesKey("card_opacity")
        val unit = stringPreferencesKey("unit")
        val speedAlert = intPreferencesKey("speed_alert")
        val speedVolume = booleanPreferencesKey("speed_volume")
        val layoutMode = stringPreferencesKey("layout_mode")
        val dockPosition = stringPreferencesKey("dock_position")
        val dockApps = stringPreferencesKey("dock_apps")
        val layout = stringPreferencesKey("layout")
        val navPackage = stringPreferencesKey("nav_package")
        val obdAddress = stringPreferencesKey("obd_address")
        val obdAuto = booleanPreferencesKey("obd_auto")
        val ambientAddress = stringPreferencesKey("ambient_address")
        val ambientMatch = booleanPreferencesKey("ambient_match")
        val ssMinutes = intPreferencesKey("ss_minutes")
        val ssDim = floatPreferencesKey("ss_dim")
        val autostart = stringPreferencesKey("autostart")
        val autostartDelay = intPreferencesKey("autostart_delay")
        val coolant = intPreferencesKey("coolant_alert")
        val voltage = floatPreferencesKey("voltage_alert")
        val shiftRpm = intPreferencesKey("shift_rpm")
        val note = stringPreferencesKey("note")
        val speedStyle = stringPreferencesKey("speed_style")
        val rpmStyle = stringPreferencesKey("rpm_style")
        val speedColor = stringPreferencesKey("speed_color")
        val dialColor = stringPreferencesKey("dial_color")
        val panelRpm = booleanPreferencesKey("panel_rpm")
    }

    val settings: Flow<Settings> = context.store.data.map { readFrom(it, Settings()) }

    suspend fun update(transform: (Settings) -> Settings) {
        context.store.edit { p ->
            val s = transform(readFrom(p, Settings()))
            p[K.accent] = s.accent
            p[K.themeMode] = s.themeMode.name
            p[K.background] = s.background.name
            p[K.cardOpacity] = s.cardOpacity
            p[K.unit] = s.unit.name
            p[K.speedAlert] = s.speedAlert
            p[K.speedVolume] = s.speedVolume
            p[K.layoutMode] = s.layoutMode.name
            p[K.dockPosition] = s.dockPosition.name
            p[K.dockApps] = s.dockApps.joinToString("\n")
            p[K.layout] = s.layout.encode()
            p[K.navPackage] = s.navPackage
            p[K.obdAddress] = s.obdAddress
            p[K.obdAuto] = s.obdAutoConnect
            p[K.ambientAddress] = s.ambientAddress
            p[K.ambientMatch] = s.ambientMatchAccent
            p[K.ssMinutes] = s.screensaverMinutes
            p[K.ssDim] = s.screensaverDim
            p[K.autostart] = s.autostartPackage
            p[K.autostartDelay] = s.autostartDelaySec
            p[K.coolant] = s.coolantAlertC
            p[K.voltage] = s.lowVoltageAlert
            p[K.shiftRpm] = s.shiftRpm
            p[K.note] = s.noteText
            p[K.speedStyle] = s.speedStyle.name
            p[K.rpmStyle] = s.rpmStyle.name
            p[K.speedColor] = s.speedColor.name
            p[K.dialColor] = s.dialColor.name
            p[K.panelRpm] = s.panelRpm
        }
    }

    private fun readFrom(p: Preferences, d: Settings) = Settings(
        accent = p[K.accent] ?: d.accent,
        themeMode = enumOr(p[K.themeMode], d.themeMode),
        background = enumOr(p[K.background], d.background),
        cardOpacity = p[K.cardOpacity] ?: d.cardOpacity,
        unit = enumOr(p[K.unit], d.unit),
        speedAlert = p[K.speedAlert] ?: d.speedAlert,
        speedVolume = p[K.speedVolume] ?: d.speedVolume,
        layoutMode = enumOr(p[K.layoutMode], d.layoutMode),
        dockPosition = enumOr(p[K.dockPosition], d.dockPosition),
        dockApps = p[K.dockApps]?.split('\n')?.filter { it.isNotBlank() } ?: d.dockApps,
        layout = p[K.layout]?.let { DashboardLayout.decode(it) } ?: d.layout,
        navPackage = p[K.navPackage] ?: d.navPackage,
        obdAddress = p[K.obdAddress] ?: d.obdAddress,
        obdAutoConnect = p[K.obdAuto] ?: d.obdAutoConnect,
        ambientAddress = p[K.ambientAddress] ?: d.ambientAddress,
        ambientMatchAccent = p[K.ambientMatch] ?: d.ambientMatchAccent,
        screensaverMinutes = p[K.ssMinutes] ?: d.screensaverMinutes,
        screensaverDim = p[K.ssDim] ?: d.screensaverDim,
        autostartPackage = p[K.autostart] ?: d.autostartPackage,
        autostartDelaySec = p[K.autostartDelay] ?: d.autostartDelaySec,
        coolantAlertC = p[K.coolant] ?: d.coolantAlertC,
        lowVoltageAlert = p[K.voltage] ?: d.lowVoltageAlert,
        shiftRpm = p[K.shiftRpm] ?: d.shiftRpm,
        noteText = p[K.note] ?: d.noteText,
        speedStyle = enumOr(p[K.speedStyle], d.speedStyle),
        rpmStyle = enumOr(p[K.rpmStyle], d.rpmStyle),
        speedColor = enumOr(p[K.speedColor], d.speedColor),
        dialColor = enumOr(p[K.dialColor], d.dialColor),
        panelRpm = p[K.panelRpm] ?: d.panelRpm,
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
