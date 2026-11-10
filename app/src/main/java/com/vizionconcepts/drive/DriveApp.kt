package com.vizionconcepts.drive

import android.app.Application
import com.vizionconcepts.drive.ambient.AmbientLightManager
import com.vizionconcepts.drive.media.MediaHub
import com.vizionconcepts.drive.obd.ObdManager
import com.vizionconcepts.drive.settings.SettingsRepository
import com.vizionconcepts.drive.system.AppsRepository
import com.vizionconcepts.drive.system.CarSystem
import com.vizionconcepts.drive.vehicle.LocationTracker
import com.vizionconcepts.drive.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Simple manual dependency container — one instance of each service for the whole app. */
class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val apps = AppsRepository(app)
    val obd = ObdManager(app)
    val ambient = AmbientLightManager(app)
    val gps = LocationTracker(app)
    val media = MediaHub(app)
    val weather = WeatherRepository()
    val car = CarSystem(app)

    /** Last user touch, used by the screensaver. */
    val lastInteraction = MutableStateFlow(System.currentTimeMillis())

    /** True once the autostart app has been handled since this boot. */
    var autostartDone = false

    /** True while the home screen is in move/resize mode. */
    val editLayout = MutableStateFlow(false)

    /** Page currently shown on the home screen (the editor adds widgets here). */
    val currentPage = MutableStateFlow(0)

    /** Non-null while the autostart countdown banner is showing. */
    val autostart = MutableStateFlow<AutostartCountdown?>(null)
}

data class AutostartCountdown(val label: String, val secondsLeft: Int)

class DriveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
