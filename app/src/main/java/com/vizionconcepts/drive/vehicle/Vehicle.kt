package com.vizionconcepts.drive.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsState(
    val hasFix: Boolean = false,
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val altitudeM: Double = 0.0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val tripKm: Double = 0.0,
    val tripMovingMs: Long = 0L,
    val tripAvgMps: Float = 0f,
    val tripMaxMps: Float = 0f,
)

class LocationTracker(context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val trip = TripComputer()
    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state.asStateFlow()
    private var started = false

    private var prevLat = Double.NaN
    private var prevLon = Double.NaN
    private var prevTime = 0L
    private var smoothed = 0f
    private var lastGpsFix = 0L

    private val listener = LocationListener { loc: Location ->
        val isGps = loc.provider == LocationManager.GPS_PROVIDER
        // Network fixes are only a stand-in until the GPS locks on.
        if (!isGps && System.currentTimeMillis() - lastGpsFix < 5_000L) return@LocationListener
        if (isGps) lastGpsFix = System.currentTimeMillis()
        // Many head-unit GPS modules don't report speed: work it out from the last two fixes instead.
        val derived = TripComputer.derivedSpeed(prevLat, prevLon, prevTime, loc.latitude, loc.longitude, loc.time)
        val raw = when {
            loc.hasSpeed() && loc.speed > 0f -> loc.speed
            derived != null -> derived
            loc.hasSpeed() -> 0f
            else -> smoothed
        }
        smoothed = if (loc.hasSpeed() && loc.speed > 0f) raw else smoothed * 0.4f + raw * 0.6f
        val speed = if (smoothed < 0.5f) 0f else smoothed
        prevLat = loc.latitude; prevLon = loc.longitude; prevTime = loc.time
        trip.onFix(loc.latitude, loc.longitude, speed, loc.time, if (loc.hasAccuracy()) loc.accuracy else 10f)
        val prev = _state.value
        _state.value = GpsState(
            hasFix = true,
            speedMps = speed,
            bearing = if (loc.hasBearing() && speed > 1.5f) loc.bearing else prev.bearing,
            altitudeM = if (loc.hasAltitude()) loc.altitude else prev.altitudeM,
            lat = loc.latitude, lon = loc.longitude,
            tripKm = trip.distanceM / 1000.0,
            tripMovingMs = trip.movingMs,
            tripAvgMps = trip.averageMps,
            tripMaxMps = trip.maxMps,
        )
    }

    /** What the GPS is doing, for the status line in Settings. */
    val status: String get() = when {
        !started -> "Not running – location permission needed"
        !runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> "GPS is switched off in Android settings"
        !_state.value.hasFix -> "Waiting for satellites…"
        else -> "Locked on"
    }

    /** Safe to call repeatedly (e.g. on every resume): only registers once, and retries if an earlier start failed. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        val gps = runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper()); true }.getOrDefault(false)
        val net = runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper()); true }.getOrDefault(false)
        started = gps || net
    }

    fun stop() {
        runCatching { lm.removeUpdates(listener) }
        started = false
    }

    fun resetTrip() {
        trip.reset()
        _state.value = _state.value.copy(tripKm = 0.0, tripMovingMs = 0, tripAvgMps = 0f, tripMaxMps = 0f)
    }
}
