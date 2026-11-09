package com.vizionconcepts.drive.system

object SpeedVolume {
    fun stepsFor(kmh: Float): Int = if (kmh <= 40f) 0 else (((kmh - 40f) / 30f).toInt() + 1).coerceAtMost(4)
}
