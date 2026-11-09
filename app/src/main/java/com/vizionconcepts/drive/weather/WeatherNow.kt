package com.vizionconcepts.drive.weather

data class WeatherNow(
    val loaded: Boolean = false,
    val tempC: Double = 0.0,
    val code: Int = 0,
    val windKmh: Double = 0.0,
    val error: String = "",
) {
    val description: String get() = describe(code)
    val icon: String get() = iconFor(code)

    companion object {
        /** WMO weather interpretation codes (public standard used by Open-Meteo). */
        fun describe(code: Int) = when (code) {
            0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Overcast"
            45, 48 -> "Fog"; in 51..57 -> "Drizzle"; in 61..67 -> "Rain"
            in 71..77 -> "Snow"; in 80..82 -> "Showers"; 85, 86 -> "Snow showers"
            in 95..99 -> "Thunderstorm"; else -> "—"
        }
        fun iconFor(code: Int) = when (code) {
            0 -> "☀"; 1, 2 -> "⛅"; 3 -> "☁"; 45, 48 -> "🌫"
            in 51..67, in 80..82 -> "🌧"; in 71..77, 85, 86 -> "❄"; in 95..99 -> "⛈"; else -> "·"
        }
    }
}
