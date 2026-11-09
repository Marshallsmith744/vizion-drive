package com.vizionconcepts.drive.vehicle

/** Pure trip maths so it can be unit-tested without Android. */
class TripComputer {
    var distanceM = 0.0; private set
    var movingMs = 0L; private set
    var maxMps = 0f; private set
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastTime = 0L

    /** Feed a fix. Ignores jitter while stationary and unrealistic jumps. */
    fun onFix(lat: Double, lon: Double, speedMps: Float, timeMs: Long, accuracyM: Float = 10f) {
        if (accuracyM > 50f) return
        if (!lastLat.isNaN() && timeMs > lastTime) {
            val dt = timeMs - lastTime
            val d = haversine(lastLat, lastLon, lat, lon)
            val impliedSpeed = d / (dt / 1000.0)
            if (speedMps > 0.8f && impliedSpeed < 90.0) { // 90 m/s = 324 km/h sanity cap
                distanceM += d
                movingMs += dt
            }
        }
        if (speedMps > maxMps) maxMps = speedMps
        lastLat = lat; lastLon = lon; lastTime = timeMs
    }

    val averageMps: Float get() = if (movingMs < 1000) 0f else (distanceM / (movingMs / 1000.0)).toFloat()

    fun reset() {
        distanceM = 0.0; movingMs = 0L; maxMps = 0f
        lastLat = Double.NaN; lastLon = Double.NaN; lastTime = 0L
    }

    companion object {
        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
            return 2 * r * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))
        }

        /** 0–359° to N/NE/E/... */
        fun cardinal(bearing: Float): String {
            val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            return dirs[(((bearing % 360f) + 360f) % 360f / 45f + 0.5f).toInt() % 8]
        }
    }
}
