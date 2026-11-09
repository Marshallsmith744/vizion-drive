package com.vizionconcepts.drive.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Open-Meteo current weather. Free, no API key. Check their terms before commercial use. */
class WeatherRepository {
    private val _state = MutableStateFlow(WeatherNow())
    val state: StateFlow<WeatherNow> = _state.asStateFlow()
    private var lastFetch = 0L

    suspend fun refresh(lat: Double, lon: Double, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetch < 15 * 60_000L && _state.value.loaded) return
        lastFetch = now
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,wind_speed_10m",
            lat, lon,
        )
        _state.value = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val cur = JSONObject(body).getJSONObject("current")
                WeatherNow(
                    loaded = true,
                    tempC = cur.getDouble("temperature_2m"),
                    code = cur.getInt("weather_code"),
                    windKmh = cur.optDouble("wind_speed_10m", 0.0),
                )
            }.getOrElse { WeatherNow(loaded = _state.value.loaded, tempC = _state.value.tempC, code = _state.value.code, error = "Offline") }
        }
    }
}
