package com.vizionconcepts.drive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.settings.Settings
import com.vizionconcepts.drive.ui.HomeRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {

    private val c get() = (application as DriveApp).container

    /** Set by the UI to close overlays when HOME is pressed while we're already in front. */
    var onHomePressed: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionsChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // A launcher never "goes back" to anything.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { onHomePressed?.invoke() }
        })

        setContent { HomeRoot(container = c, activity = this) }

        requestPermissionsIfNeeded()
        startBackgroundWork()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Pressing HOME while already home: close drawers/settings.
        onHomePressed?.invoke()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        c.apps.reload()
        c.media.refresh()
        c.car.syncVolume()
        c.lastInteraction.value = System.currentTimeMillis()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        c.lastInteraction.value = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun requestPermissionsIfNeeded() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray()) else onPermissionsChanged()
    }

    private fun onPermissionsChanged() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) c.gps.start()
        lifecycleScope.launch { autoConnect(c.settings.settings.first()) }
    }

    private fun autoConnect(s: Settings) {
        if (s.obdAutoConnect && s.obdAddress.isNotBlank() && !c.obd.state.value.connected) c.obd.connect(s.obdAddress)
        if (s.ambientAddress.isNotBlank() && c.ambient.state.value.connectedTo.isBlank()) c.ambient.connect(s.ambientAddress)
    }

    private fun startBackgroundWork() {
        // Weather: refresh when we have a fix, at most every 15 minutes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val g = c.gps.state.value
                    if (g.hasFix) c.weather.refresh(g.lat, g.lon)
                    delay(60_000)
                }
            }
        }

        // Driving alerts + speed-adaptive volume.
        lifecycleScope.launch {
            combine(c.gps.state, c.obd.state, c.settings.settings) { g, o, s -> Triple(g, o, s) }
                .collectLatest { (g, o, s) ->
                    val obdSpeed = o[ObdPid.SPEED]
                    val kmh = obdSpeed ?: (g.speedMps * 3.6f)
                    val shown = kmh / 3.6f * s.unit.fromMps
                    if (s.speedAlert > 0 && shown > s.speedAlert + 1) c.car.alert("Over ${s.speedAlert} ${s.unit.label}")
                    o[ObdPid.COOLANT]?.let { if (it >= s.coolantAlertC) c.car.alert("Coolant ${it.toInt()}°C – engine hot") }
                    o[ObdPid.VOLTAGE]?.let { if (it in 5f..s.lowVoltageAlert) c.car.alert("Battery %.1f V – low".format(it)) }
                    if (s.speedVolume) c.car.applySpeedVolume(kmh)
                }
        }

        // Ambient light follows the accent colour when enabled.
        lifecycleScope.launch {
            combine(c.settings.settings, c.ambient.state.map { it.connectedTo }.distinctUntilChanged()) { s, addr -> s to addr }
                .collectLatest { (s, addr) ->
                    if (addr.isNotBlank() && s.ambientMatchAccent) c.ambient.setColor(s.accent)
                }
        }

        // Autostart: once per boot, only shortly after boot.
        lifecycleScope.launch {
            val s = c.settings.settings.first()
            if (!c.autostartDone && s.autostartPackage.isNotBlank() && SystemClock.elapsedRealtime() < 10 * 60_000L) {
                c.autostartDone = true
                val label = c.apps.findPackage(s.autostartPackage)?.label ?: s.autostartPackage
                for (left in s.autostartDelaySec downTo 1) {
                    c.autostart.value = AutostartCountdown(label, left)
                    delay(1000)
                    if (c.autostart.value == null) return@launch // cancelled from the banner
                }
                c.autostart.value = null
                c.apps.launchPackage(s.autostartPackage)
            }
        }
    }
}
