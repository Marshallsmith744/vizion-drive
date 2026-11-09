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

    private val listener = LocationListener { loc: Location ->
        val speed = if (loc.hasSpeed()) loc.speed else 0f
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

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
            true
        }.getOrDefault(false)
        if (!started) runCatching {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper())
            started = true
        }
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
