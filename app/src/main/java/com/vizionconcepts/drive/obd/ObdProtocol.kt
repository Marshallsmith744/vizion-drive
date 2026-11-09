package com.vizionconcepts.drive.obd

/**
 * Standard SAE J1979 mode 01 PIDs with their public decoding formulas.
 * A, B = first and second data bytes after the "41 xx" header.
 */
enum class ObdPid(
    val code: Int,
    val label: String,
    val unit: String,
    val bytes: Int,
    val min: Float,
    val max: Float,
    /** Lower number = polled more often. */
    val priority: Int,
    private val formula: (Int, Int) -> Float,
) {
    RPM(0x0C, "RPM", "rpm", 2, 0f, 8000f, 0, { a, b -> (a * 256 + b) / 4f }),
    SPEED(0x0D, "Speed", "km/h", 1, 0f, 260f, 0, { a, _ -> a.toFloat() }),
    THROTTLE(0x11, "Throttle", "%", 1, 0f, 100f, 1, { a, _ -> a * 100f / 255f }),
    ENGINE_LOAD(0x04, "Load", "%", 1, 0f, 100f, 1, { a, _ -> a * 100f / 255f }),
    COOLANT(0x05, "Coolant", "°C", 1, -40f, 130f, 2, { a, _ -> a - 40f }),
    INTAKE_TEMP(0x0F, "Intake", "°C", 1, -40f, 80f, 3, { a, _ -> a - 40f }),
    MAP(0x0B, "Boost/MAP", "kPa", 1, 0f, 255f, 1, { a, _ -> a.toFloat() }),
    MAF(0x10, "MAF", "g/s", 2, 0f, 300f, 2, { a, b -> (a * 256 + b) / 100f }),
    TIMING(0x0E, "Timing", "°", 1, -64f, 64f, 2, { a, _ -> a / 2f - 64f }),
    FUEL_LEVEL(0x2F, "Fuel", "%", 1, 0f, 100f, 3, { a, _ -> a * 100f / 255f }),
    VOLTAGE(0x42, "Battery", "V", 2, 9f, 16f, 3, { a, b -> (a * 256 + b) / 1000f }),
    AMBIENT(0x46, "Outside", "°C", 1, -40f, 60f, 3, { a, _ -> a - 40f }),
    OIL_TEMP(0x5C, "Oil", "°C", 1, -40f, 160f, 3, { a, _ -> a - 40f }),
    FUEL_RATE(0x5E, "Fuel rate", "L/h", 2, 0f, 40f, 2, { a, b -> (a * 256 + b) / 20f });

    fun decode(data: IntArray): Float? =
        if (data.size < bytes) null else formula(data[0], if (bytes > 1) data[1] else 0)

    /** Request string sent to the ELM327, e.g. "010C". */
    val request: String get() = "01%02X".format(code)
}

object ElmParser {

    /** ELM327 replies that mean "no answer from the car" rather than data. */
    private val NO_DATA = listOf("NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS INIT", "STOPPED", "ERROR", "?")

    fun isNoData(raw: String): Boolean {
        val r = raw.uppercase()
        return NO_DATA.any { r.contains(it) }
    }

    /**
     * Turns a raw ELM reply (spaces/echo/prompt allowed, multi-ECU lines allowed) into
     * the data bytes following the `41 <pid>` header. Returns null if not found.
     */
    fun mode01Data(raw: String, pid: Int): IntArray? {
        if (isNoData(raw)) return null
        val header = "41%02X".format(pid)
        for (line in raw.uppercase().split('\r', '\n')) {
            val hex = line.filter { it.isLetterOrDigit() }
            val at = hex.indexOf(header)
            if (at < 0) continue
            val body = hex.substring(at + header.length)
            if (body.length < 2) continue
            return body.chunked(2).filter { it.length == 2 }
                .mapNotNull { it.toIntOrNull(16) }.toIntArray()
        }
        return null
    }

    /**
     * Decodes a mode 03 (stored DTC) reply into codes such as "P0301".
     * Works for CAN replies ("43 02 03 01 04 20") and legacy replies ("43 03 01 04 20 00 00").
     */
    fun dtcs(raw: String): List<String> {
        if (isNoData(raw)) return emptyList()
        val codes = linkedSetOf<String>()
        for (line in raw.uppercase().split('\r', '\n')) {
            var hex = line.filter { it.isLetterOrDigit() }
            // Drop ISO-TP frame markers like "0:" "1:" which survive as a leading digit pair.
            val at = hex.indexOf("43")
            if (at < 0) continue
            hex = hex.substring(at + 2)
            // CAN replies include a count byte: odd number of remaining bytes after it means it's present.
            val bytes = hex.chunked(2).filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }
            val data = if (bytes.size % 2 == 1) bytes.drop(1) else bytes
            data.chunked(2).filter { it.size == 2 }.forEach { (a, b) ->
                if (a != 0 || b != 0) codes += decodeDtc(a, b)
            }
        }
        return codes.toList()
    }

    /** SAE J2012 two-byte DTC encoding. */
    fun decodeDtc(a: Int, b: Int): String {
        val system = when (a shr 6) { 0 -> 'P'; 1 -> 'C'; 2 -> 'B'; else -> 'U' }
        val d1 = (a shr 4) and 0x3
        val d2 = a and 0xF
        return "%c%d%X%02X".format(system, d1, d2, b)
    }

    fun systemName(code: String): String = when (code.firstOrNull()) {
        'P' -> "Powertrain"; 'C' -> "Chassis"; 'B' -> "Body"; 'U' -> "Network"; else -> "Unknown"
    }

    /** Parses "ATRV" style replies like "12.6V". */
    fun voltage(raw: String): Float? =
        Regex("""(\d{1,2}\.\d)""").find(raw)?.groupValues?.get(1)?.toFloatOrNull()
}
