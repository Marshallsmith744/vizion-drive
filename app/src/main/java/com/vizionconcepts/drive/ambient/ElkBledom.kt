package com.vizionconcepts.drive.ambient

/**
 * Command frames for ELK-BLEDOM style BLE LED strip controllers (the common
 * "duoCo Strip"/"Lotus Lantern" family). Format is publicly documented by the
 * hobbyist community: 9 bytes, 0x7E ... 0xEF.
 */
object ElkBledom {
    fun power(on: Boolean): ByteArray =
        bytes(0x7E, 0x00, 0x04, if (on) 0xF0 else 0x00, 0x00, if (on) 0x01 else 0x00, 0xFF, 0x00, 0xEF)

    fun color(r: Int, g: Int, b: Int): ByteArray =
        bytes(0x7E, 0x00, 0x05, 0x03, r.clamp(), g.clamp(), b.clamp(), 0x00, 0xEF)

    /** brightness 0..100 */
    fun brightness(percent: Int): ByteArray =
        bytes(0x7E, 0x00, 0x01, percent.coerceIn(0, 100), 0x00, 0x00, 0x00, 0x00, 0xEF)

    /** Built-in effect index (0x80 + n on most firmwares) and speed 0..100. */
    fun effect(index: Int): ByteArray = bytes(0x7E, 0x00, 0x03, 0x80 + index.coerceIn(0, 0x1C), 0x03, 0x00, 0x00, 0x00, 0xEF)
    fun effectSpeed(percent: Int): ByteArray = bytes(0x7E, 0x00, 0x02, percent.coerceIn(0, 100), 0x00, 0x00, 0x00, 0x00, 0xEF)

    fun rgbOf(argb: Long): Triple<Int, Int, Int> =
        Triple(((argb shr 16) and 0xFF).toInt(), ((argb shr 8) and 0xFF).toInt(), (argb and 0xFF).toInt())

    fun looksLikeController(name: String?): Boolean {
        val n = name?.uppercase() ?: return false
        return n.startsWith("ELK-") || n.startsWith("ELK_") || n.contains("BLEDOM") || n.startsWith("MELK") || n.startsWith("LEDBLE")
    }

    private fun Int.clamp() = coerceIn(0, 255)
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}
