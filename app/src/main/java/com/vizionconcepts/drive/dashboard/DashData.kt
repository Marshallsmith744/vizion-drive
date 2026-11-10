package com.vizionconcepts.drive.dashboard

import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.obd.ObdSnapshot
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
        "V", "psi" -> String.format(Locale.US, "%.1f", v)
        else -> v.roundToInt().toString()
    }

    /** Clock hand angles in degrees clockwise from 12 o'clock: hour, minute, second. */
    fun handAngles(h: Int, m: Int, s: Int): Triple<Float, Float, Float> =
        Triple(((h % 12) + m / 60f) * 30f, (m + s / 60f) * 6f, s * 6f)

    /** Tacho scale top in thousands of rpm: one whole number above the shift point, at least 8. */
    fun tachoMax(shiftRpm: Int): Int = maxOf(8, shiftRpm / 1000 + 2)
}

/** The values a race-dash tile can show, read from OBD data. */
enum class DashChannel(
    val label: String,
    val unit: String,
    val min: Float,
    val max: Float,
    private val read: (ObdSnapshot) -> Float?,
) {
    MANIFOLD("Manifold", "psi", -15f, 10f, { o -> o[ObdPid.MAP]?.let(DashMath::boostPsi) }),
    THROTTLE("Throttle", "%", 0f, 100f, { it[ObdPid.THROTTLE] }),
    LOAD("Engine load", "%", 0f, 100f, { it[ObdPid.ENGINE_LOAD] }),
    INTAKE("Intake temp", "°C", 0f, 80f, { it[ObdPid.INTAKE_TEMP] }),
    COOLANT("Coolant temp", "°C", 40f, 120f, { it[ObdPid.COOLANT] }),
    OIL("Oil temp", "°C", 40f, 150f, { it[ObdPid.OIL_TEMP] }),
    BATTERY("Battery", "V", 11f, 15f, { it[ObdPid.VOLTAGE] }),
    FUEL("Fuel level", "%", 0f, 100f, { it[ObdPid.FUEL_LEVEL] });

    fun value(o: ObdSnapshot): Float? = read(o)
    fun fraction(o: ObdSnapshot): Float = value(o)?.let { DashMath.fraction(it, min, max) } ?: 0f
    fun text(o: ObdSnapshot): String = value(o)?.let { DashMath.format(it, unit) } ?: "--"

    companion object {
        val LEFT = listOf(MANIFOLD, THROTTLE, LOAD, INTAKE)
        val RIGHT = listOf(COOLANT, OIL, BATTERY, FUEL)
    }
}
