package com.vizionconcepts.drive.dashboard

import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.obd.ObdSnapshot
import com.vizionconcepts.drive.vehicle.GpsState
import java.util.Locale
import kotlin.math.roundToInt

/** Plain maths used by the digital dashes, kept free of UI so it can be unit tested. */
object DashMath {
    private const val ATMOSPHERE_KPA = 101.325f
    private const val KPA_TO_PSI = 0.1450377f

    fun fraction(v: Float, min: Float, max: Float): Float =
        if (max <= min) 0f else ((v - min) / (max - min)).coerceIn(0f, 1f)

    /** Manifold absolute pressure → gauge pressure in psi (negative = vacuum). */
    fun boostPsi(mapKpa: Float): Float = (mapKpa - ATMOSPHERE_KPA) * KPA_TO_PSI

    /** How many of [n] bar segments are lit for a 0..1 fraction. */
    fun segmentsLit(fraction: Float, n: Int): Int = (fraction.coerceIn(0f, 1f) * n).roundToInt().coerceIn(0, n)

    fun format(v: Float, unit: String): String = when (unit) {
        "V", "psi", "km" -> String.format(Locale.US, "%.1f", v)
        else -> v.roundToInt().toString()
    }

    /** Clock hand angles in degrees clockwise from 12 o'clock: hour, minute, second. */
    fun handAngles(h: Int, m: Int, s: Int): Triple<Float, Float, Float> =
        Triple(((h % 12) + m / 60f) * 30f, (m + s / 60f) * 6f, s * 6f)

    /** Tacho scale top in thousands of rpm: one whole number above the shift point, at least 8. */
    fun tachoMax(shiftRpm: Int): Int = maxOf(8, shiftRpm / 1000 + 2)
}

/**
 * The values the digital dashes show. Only things the head unit itself can know are included:
 * signals the car's body CANbus carries to the head unit through the CAN decoder (rpm, speed,
 * coolant, fuel level, outside temperature) and what the head unit measures (GPS trip).
 * Engine-computer-only values (boost, throttle, load, intake/oil temp, AFR) are deliberately left out.
 */
enum class DashChannel(
    val label: String,
    val unit: String,
    val min: Float,
    val max: Float,
    private val read: (ObdSnapshot, GpsState) -> Float?,
) {
    COOLANT("Coolant temp", "°C", 40f, 120f, { o, _ -> o[ObdPid.COOLANT] }),
    FUEL("Fuel level", "%", 0f, 100f, { o, _ -> o[ObdPid.FUEL_LEVEL] }),
    OUTSIDE("Outside temp", "°C", -10f, 45f, { o, _ -> o[ObdPid.AMBIENT] }),
    TRIP("Trip", "km", 0f, 500f, { _, g -> g.tripKm.toFloat() });

    fun value(o: ObdSnapshot, g: GpsState = GpsState()): Float? = read(o, g)
    fun fraction(o: ObdSnapshot, g: GpsState = GpsState()): Float = value(o, g)?.let { DashMath.fraction(it, min, max) } ?: 0f
    fun text(o: ObdSnapshot, g: GpsState = GpsState()): String = value(o, g)?.let { DashMath.format(it, unit) } ?: "--"

    companion object {
        val LEFT = listOf(COOLANT, FUEL)
        val RIGHT = listOf(OUTSIDE, TRIP)
    }
}
