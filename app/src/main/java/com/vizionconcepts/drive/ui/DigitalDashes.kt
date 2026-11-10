package com.vizionconcepts.drive.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.vizionconcepts.drive.dashboard.DashChannel
import com.vizionconcepts.drive.dashboard.DashMath
import com.vizionconcepts.drive.obd.ObdPid
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Two full-width digital dashes, drawn entirely on Canvas so they scale to any widget size.
 *  - RaceDash: motorsport-display look — big two-colour tacho in the middle, data tiles with
 *    segment meters down both sides, gear/speed/rpm boxes.
 *  - TwinDash: two large multi-dial clusters (speed + watch face) with a digital centre,
 *    rainbow level bars on the edges and small sub-gauges.
 * Both use the dash colour from Settings → Digital Dash, and only show values the head unit can
 * get on its own (CANbus via the CAN decoder, plus GPS) — see DashChannel.
 */

private val DashRed = Color(0xFFFF2A2A)
private val DashWhite = Color(0xFFF4F6F8)
private val DashDim = Color(0xFF8A9099)

private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val condensedBold: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
private val condensedBoldItalic: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
private val plain: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val condensedMedium: Typeface = Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL)

/** Draws text with its vertical centre at [y]. */
private fun DrawScope.label(
    s: String, x: Float, y: Float, size: Float, color: Color,
    align: Paint.Align = Paint.Align.CENTER, face: Typeface = condensedBold, spacing: Float = 0f,
) {
    textPaint.letterSpacing = spacing
    textPaint.textSize = size
    textPaint.color = color.toArgb()
    textPaint.textAlign = align
    textPaint.typeface = face
    drawContext.canvas.nativeCanvas.drawText(s, x, y + size * 0.36f, textPaint)
}

private fun at(c: Offset, r: Float, deg: Float) =
    Offset(c.x + r * cos(Math.toRadians(deg.toDouble())).toFloat(), c.y + r * sin(Math.toRadians(deg.toDouble())).toFloat())

@Composable
private fun rememberNow(): Calendar {
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            value = Calendar.getInstance()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    return now
}

// ───────────────────────────── Race dash ─────────────────────────────

private const val RACE_START = 120f   // 0 rpm at lower left; the open quarter at lower right holds the boxes
private const val RACE_SWEEP = 270f

@Composable
fun RaceDash(ui: UiState, modifier: Modifier = Modifier) {
    val rpmRaw = ui.obd[ObdPid.RPM]
    val rpm = rpmRaw ?: 0f
    val maxK = DashMath.tachoMax(ui.settings.shiftRpm)
    val f by animateFloatAsState(DashMath.fraction(rpm, 0f, maxK * 1000f), tween(180), label = "race")
    var peak by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rpm) { if (rpm > peak) peak = rpm }
    val accent = Color(ui.settings.dashColor)
    val shiftK = ui.settings.shiftRpm / 1000f
    val obd = ui.obd
    val gps = ui.gps
    val speed = ui.speedShown
    val unit = ui.settings.unit.label
    val coolant = obd[ObdPid.COOLANT]
    val fuel = obd[ObdPid.FUEL_LEVEL]
    val lights = listOf(
        Triple("SHIFT", rpm >= ui.settings.shiftRpm, RaceRed),
        Triple("TEMP", coolant != null && coolant >= ui.settings.coolantAlertC, RaceRed),
        Triple("FUEL", fuel != null && fuel < 15f, Color(0xFFFFA000)),
        Triple("GPS", gps.hasFix, Color(0xFF1ED760)),
    )
    Box(modifier.clip(RoundedCornerShape(22.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.radialGradient(listOf(Color(0xFF1D1F23), Color(0xFF060607)), Offset(w / 2f, h * 0.45f), maxOf(w, h) * 0.75f))
            val pad = h * 0.03f
            val tileW = w * 0.19f
            val tileH = (h - pad * 3) / 2f
            DashChannel.LEFT.forEachIndexed { i, ch ->
                raceTile(Rect(Offset(pad, pad + i * (tileH + pad)), Size(tileW, tileH)), ch.text(obd, gps), ch.unit, ch.label, ch.fraction(obd, gps), accent, left = true)
            }
            DashChannel.RIGHT.forEachIndexed { i, ch ->
                raceTile(Rect(Offset(w - pad - tileW, pad + i * (tileH + pad)), Size(tileW, tileH)), ch.text(obd, gps), ch.unit, ch.label, ch.fraction(obd, gps), accent, left = false)
            }

            // ── Centre tacho ──
            val c = Offset(w / 2f, h / 2f)
            val r = min((w - 2 * tileW - 4 * pad) / 2f, h / 2f - pad * 0.6f) * 0.93f
            val inner = r * 0.6f
            val band = r - inner
            val mid = (r + inner) / 2f
            val shiftF = (shiftK / maxK).coerceIn(0f, 1f)
            val dark = Color(0xFF0A0B0C)
            // Drop shadow and bevelled gunmetal bezel
            drawCircle(Brush.radialGradient(0.8f to Color.Black.copy(alpha = 0.7f), 1f to Color.Transparent, center = Offset(c.x, c.y + r * 0.03f), radius = r * 1.2f), r * 1.2f, Offset(c.x, c.y + r * 0.03f))
            drawCircle(
                Brush.verticalGradient(0f to Color(0xFF7C828B), 0.25f to Color(0xFF3B3F45), 0.6f to Color(0xFF17181B), 1f to Color(0xFF4A4F56),
                    startY = c.y - r * 1.085f, endY = c.y + r * 1.085f),
                r * 1.085f, c,
            )
            drawCircle(Color.White.copy(alpha = 0.22f), r * 1.08f, c, style = Stroke(1.2f))
            drawCircle(Color(0xFF08090A), r * 1.03f, c)
            // Permanently coloured scale, shaded like a moulded part; red zone from the shift point.
            fun shade(col: Color) = Brush.radialGradient(
                0.6f to lerp(col, Color.Black, 0.45f), 0.72f to col, 0.9f to lerp(col, Color.White, 0.25f), 1f to lerp(col, Color.Black, 0.2f),
                center = c, radius = r,
            )
            val tl = Offset(c.x - mid, c.y - mid)
            val sz = Size(mid * 2, mid * 2)
            drawArc(shade(accent), RACE_START, RACE_SWEEP * shiftF, false, tl, sz, style = Stroke(band))
            drawArc(shade(RaceRed), RACE_START + RACE_SWEEP * shiftF, RACE_SWEEP * (1 - shiftF), false, tl, sz, style = Stroke(band))
            val gr = r - band * 0.18f
            drawArc(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.5f to Color.Transparent, startY = c.y - r, endY = c.y + r),
                RACE_START, RACE_SWEEP, false, Offset(c.x - gr, c.y - gr), Size(gr * 2, gr * 2), style = Stroke(band * 0.36f),
            )
            for (k in 0..maxK) {
                val a = RACE_START + RACE_SWEEP * k / maxK
                drawLine(dark, at(c, inner, a), at(c, r, a), strokeWidth = r * 0.014f)
                if (k < maxK) {
                    for (q in 1..3) {
                        val qa = a + RACE_SWEEP / maxK * q / 4f
                        drawLine(dark.copy(alpha = 0.85f), at(c, r, qa), at(c, r - band * (if (q == 2) 0.26f else 0.14f), qa), strokeWidth = r * 0.008f)
                    }
                    val n = at(c, mid - band * 0.04f, a + RACE_SWEEP / maxK / 2f)
                    label("$k", n.x, n.y, band * 0.52f, Color(0xFF0B0B0C))
                }
            }
            // Separator, progress ring, face
            drawCircle(Color(0xFF050506), inner, c)
            drawCircle(accent.copy(alpha = 0.9f), inner - r * 0.012f, c, style = Stroke(r * 0.006f))
            val pr = inner - r * 0.045f
            val prTl = Offset(c.x - pr, c.y - pr)
            drawArc(Color(0xFF1C1E22), RACE_START, RACE_SWEEP, false, prTl, Size(pr * 2, pr * 2), style = Stroke(r * 0.022f))
            drawArc(if (f > shiftF) RaceRed else accent, RACE_START, RACE_SWEEP * f, false, prTl, Size(pr * 2, pr * 2), style = Stroke(r * 0.022f, cap = StrokeCap.Round))
            val fc = inner - r * 0.075f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1B1D21), Color(0xFF030304)), Offset(c.x, c.y - fc * 0.2f), fc * 1.2f), fc, c)
            for (k in 1..3) drawCircle(Color.White.copy(alpha = 0.035f), fc * (0.35f + 0.18f * k), c, style = Stroke(1f))
            label("RPM ×1000", c.x, c.y - fc * 0.66f, fc * 0.085f, Color(0xFF7D838B), face = condensedMedium, spacing = 0.2f)
            // Warning lights — only things the head unit actually knows
            val lw = fc * 0.3f
            val lh = fc * 0.13f
            lights.forEachIndexed { i, (name, on, col) ->
                val lx = c.x + (i - 1.5f) * (lw + fc * 0.035f) - lw / 2f
                val ly = c.y - fc * 0.5f
                if (on) drawRoundRect(col.copy(alpha = 0.35f), Offset(lx - 4f, ly - 4f), Size(lw + 8f, lh + 8f), CornerRadius(lh))
                drawRoundRect(if (on) col else Color(0xFF0E0F11), Offset(lx, ly), Size(lw, lh), CornerRadius(lh / 2f))
                drawRoundRect(if (on) col else Color(0xFF2E3135), Offset(lx, ly), Size(lw, lh), CornerRadius(lh / 2f), style = Stroke(1.2f))
                label(name, lx + lw / 2f, ly + lh / 2f, lh * 0.52f, if (on) Color(0xFF0B0B0C) else Color(0xFF4A4F56), spacing = 0.08f)
            }
            label(if (rpmRaw != null) "${rpm.toInt()}" else "----", c.x, c.y + fc * 0.36f, fc * 0.2f, Color(0xFFF2F3F5), spacing = 0.05f)
            label("RPM", c.x, c.y + fc * 0.52f, fc * 0.075f, Color(0xFF7D838B), face = condensedMedium, spacing = 0.2f)
            // Peak-rpm marker
            if (peak > 0f) {
                val pa = RACE_START + RACE_SWEEP * DashMath.fraction(peak, 0f, maxK * 1000f)
                val tip = at(c, r, pa)
                val b1 = at(c, r * 1.075f, pa - 2f)
                val b2 = at(c, r * 1.075f, pa + 2f)
                val tri = Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }
                drawPath(tri, DashWhite)
                drawPath(tri, Color.Black, style = Stroke(1f))
            }
            // Tapered needle with glow and highlight
            val na = RACE_START + RACE_SWEEP * f
            val tipW = Math.toDegrees(kotlin.math.atan((r * 0.006f / (r * 0.97f)).toDouble())).toFloat()
            val needle = Path().apply {
                val p1 = at(c, r * 0.97f, na - tipW); moveTo(p1.x, p1.y)
                val p2 = at(c, r * 0.97f, na + tipW); lineTo(p2.x, p2.y)
                val p3 = at(c, r * 0.03f, na + 90f); lineTo(p3.x, p3.y)
                val p4 = at(c, -r * 0.16f, na + 4f); lineTo(p4.x, p4.y)
                val p5 = at(c, -r * 0.16f, na - 4f); lineTo(p5.x, p5.y)
                val p6 = at(c, r * 0.03f, na - 90f); lineTo(p6.x, p6.y)
                close()
            }
            drawPath(needle, RaceRed.copy(alpha = 0.3f), style = Stroke(r * 0.03f))
            drawPath(needle, RaceRed)
            drawLine(Color(0xFFFF8A8A), at(c, r * 0.05f, na), at(c, r * 0.93f, na), strokeWidth = r * 0.006f, cap = StrokeCap.Round)
            drawCircle(Brush.verticalGradient(listOf(Color(0xFFD9DCE0), Color(0xFF5A5F66), Color(0xFF2A2D32)), c.y - r * 0.085f, c.y + r * 0.085f), r * 0.085f, c)
            drawCircle(Color(0xFF111215), r * 0.06f, c)
            drawCircle(RaceRed, r * 0.022f, c)

            // Large digital speed: brushed frame with an LCD inset
            val sb = Rect(Offset(c.x + r * 0.04f, c.y + r * 0.6f), Size(r * 1.0f, r * 0.42f))
            val m = sb.height * 0.07f
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF5E6269), Color(0xFF2E3135), Color(0xFF1A1B1E)), sb.top, sb.bottom), sb.topLeft, sb.size, CornerRadius(sb.height * 0.08f))
            drawRoundRect(Color(0xFF050506), sb.topLeft, sb.size, CornerRadius(sb.height * 0.08f), style = Stroke(2f))
            drawLine(Color.White.copy(alpha = 0.35f), Offset(sb.left + sb.height * 0.08f, sb.top + 1.5f), Offset(sb.right - sb.height * 0.08f, sb.top + 1.5f), strokeWidth = 1.2f)
            val lcd = Rect(sb.left + m, sb.top + m, sb.right - m, sb.bottom - m)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF15171A), Color(0xFF050506)), lcd.top, lcd.bottom), lcd.topLeft, lcd.size, CornerRadius(sb.height * 0.05f))
            drawRoundRect(Color.Black, lcd.topLeft, lcd.size, CornerRadius(sb.height * 0.05f), style = Stroke(1.5f))
            label("SPEED", lcd.left + sb.height * 0.12f, sb.top + sb.height * 0.36f, sb.height * 0.13f, Color(0xFF7D838B), Paint.Align.LEFT, condensedMedium, 0.2f)
            label(unit.uppercase(Locale.US), lcd.left + sb.height * 0.12f, sb.top + sb.height * 0.62f, sb.height * 0.17f, accent, Paint.Align.LEFT, spacing = 0.08f)
            label("$speed", lcd.right - sb.height * 0.1f, sb.center.y, sb.height * 0.74f, Color(0xFFF7F8FA), Paint.Align.RIGHT)
        }
    }
}

private val RaceRed = Color(0xFFE8141B)

private fun DrawScope.raceTile(rect: Rect, value: String, unit: String, name: String, fraction: Float, accent: Color, left: Boolean) {
    val cr = CornerRadius(rect.height * 0.035f)
    drawRoundRect(
        Brush.verticalGradient(0f to Color(0xFF6A6E75), 0.47f to Color(0xFF3A3D42), 0.5f to Color(0xFF25272B), 1f to Color(0xFF16171A),
            startY = rect.top, endY = rect.bottom),
        rect.topLeft, rect.size, cr,
    )
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.02f)), rect.top, rect.top + rect.height * 0.48f),
        Offset(rect.left + 1.5f, rect.top + 1.5f), Size(rect.width - 3f, rect.height * 0.48f), cr)
    drawRoundRect(Color(0xFF050506), rect.topLeft, rect.size, cr, style = Stroke(2f))
    drawLine(Color.White.copy(alpha = 0.35f), Offset(rect.left + rect.height * 0.03f, rect.top + 1.5f), Offset(rect.right - rect.height * 0.03f, rect.top + 1.5f), strokeWidth = 1.2f)
    // Recessed segment meter on the outside edge
    val segs = 12
    val slotW = rect.width * 0.085f
    val gap = rect.height * 0.018f
    val inset = rect.height * 0.05f
    val sx = if (left) rect.left + inset else rect.right - inset - slotW
    val segH = (rect.height - 2 * inset - gap * (segs + 1)) / segs
    val lit = DashMath.segmentsLit(fraction, segs)
    drawRoundRect(Color(0xFF08090A), Offset(sx, rect.top + inset), Size(slotW, rect.height - 2 * inset), CornerRadius(3f))
    val segBrush = Brush.horizontalGradient(
        if (left) listOf(lerp(accent, Color.White, 0.25f), accent) else listOf(accent, lerp(accent, Color.White, 0.25f)),
        sx + slotW * 0.15f, sx + slotW * 0.85f,
    )
    for (i in 0 until segs) {
        val y = rect.bottom - inset - gap - (i + 1) * segH - i * gap
        if (i < lit) drawRoundRect(segBrush, Offset(sx + slotW * 0.15f, y), Size(slotW * 0.7f, segH), CornerRadius(1.5f))
        else drawRoundRect(Color(0xFF222428), Offset(sx + slotW * 0.15f, y), Size(slotW * 0.7f, segH), CornerRadius(1.5f))
    }
    // Value + unit, label underneath
    val vs = min(rect.height * 0.3f, rect.width * 0.3f)
    val textX = if (left) sx + slotW + rect.width * 0.07f else sx - rect.width * 0.07f
    val vy = rect.top + rect.height * 0.42f
    textPaint.typeface = condensedBold
    textPaint.letterSpacing = 0f
    textPaint.textSize = vs
    val vw = textPaint.measureText(value)
    textPaint.textSize = vs * 0.42f
    val uw = textPaint.measureText(unit)
    if (left) {
        label(value, textX, vy, vs, Color(0xFFF7F8FA), Paint.Align.LEFT)
        label(unit, textX + vw + vs * 0.05f, vy + vs * 0.14f, vs * 0.42f, Color(0xFFC9CDD2), Paint.Align.LEFT)
    } else {
        label(unit, textX, vy + vs * 0.14f, vs * 0.42f, Color(0xFFC9CDD2), Paint.Align.RIGHT)
        label(value, textX - uw - vs * 0.05f, vy, vs, Color(0xFFF7F8FA), Paint.Align.RIGHT)
    }
    label(name.uppercase(Locale.US), textX, rect.top + rect.height * 0.66f, vs * 0.24f, Color(0xFF9AA0A8),
        if (left) Paint.Align.LEFT else Paint.Align.RIGHT, condensedMedium, 0.12f)
}

// ───────────────────────────── Twin-dial dash ─────────────────────────────

private val Gold = Color(0xFFF2B33D)
private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

@Composable
fun TwinDash(ui: UiState, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val accent = Color(ui.settings.dashColor)
    val speed = ui.speedShown
    val speedF by animateFloatAsState(DashMath.fraction(speed.toFloat(), 0f, ui.speedMax), tween(220), label = "twinSpeed")
    val rpm = ui.obd[ObdPid.RPM]
    val rpmF by animateFloatAsState(DashMath.fraction(rpm ?: 0f, 0f, 8000f), tween(180), label = "twinRpm")
    val gps = ui.gps
    val unit = ui.settings.unit.label
    val media = ui.media
    val obd = ui.obd
    val tripMin = (gps.tripMovingMs / 60000L).toInt()
    val tripText = "%02d:%02d".format(Locale.US, tripMin / 60, tripMin % 60)
    val tripDist = String.format(Locale.US, "%.1f %s", ui.kmShown(gps.tripKm), ui.distanceUnit)

    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Bevelled frame
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFB9BEC6), Color(0xFF3A3E44), Color(0xFF9AA0A8))),
                Offset(h * 0.01f, h * 0.01f), Size(w - h * 0.02f, h - h * 0.02f), CornerRadius(h * 0.06f), style = Stroke(h * 0.012f),
            )
            // Rainbow level bars on both edges: rpm on the left (speed until rpm arrives), speed on the right
            levelBar(Rect(Offset(h * 0.035f, h * 0.18f), Size(w * 0.012f, h * 0.64f)), if (rpm != null) rpmF else speedF)
            levelBar(Rect(Offset(w - h * 0.035f - w * 0.012f, h * 0.18f), Size(w * 0.012f, h * 0.64f)), speedF)

            val r = min(w * 0.19f, h * 0.43f)
            val lc = Offset(w * 0.215f, h * 0.45f)
            val rc = Offset(w * 0.785f, h * 0.45f)
            speedCluster(lc, r, speedF, ui.speedMax, gps.bearing, rpmF, rpm != null, gps.altitudeM, accent)
            watchFace(rc, r, now, accent)

            // Trip box under the speed cluster
            val tb = Rect(Offset(lc.x - r * 0.42f, h * 0.9f - r * 0.12f), Size(r * 0.84f, r * 0.24f))
            label("TRIP TIME / DISTANCE", lc.x, tb.top - r * 0.06f, r * 0.06f, DashDim, face = medium)
            drawRoundRect(Color(0xFF15171A), tb.topLeft, tb.size, CornerRadius(r * 0.03f))
            drawRoundRect(Color(0xFF6B7078), tb.topLeft, tb.size, CornerRadius(r * 0.03f), style = Stroke(r * 0.008f))
            label("$tripText   $tripDist", lc.x, tb.center.y, r * 0.09f, DashWhite)

            // Year box under the watch face
            val yb = Rect(Offset(rc.x - r * 0.22f, h * 0.9f - r * 0.1f), Size(r * 0.44f, r * 0.2f))
            drawRoundRect(Color(0xFF15171A), yb.topLeft, yb.size, CornerRadius(r * 0.03f))
            drawRoundRect(Color(0xFF6B7078), yb.topLeft, yb.size, CornerRadius(r * 0.03f), style = Stroke(r * 0.008f))
            label("${now.get(Calendar.YEAR)}", rc.x, yb.center.y, r * 0.11f, DashWhite)

            // Centre column: clock, weekday strip, speed, media
            val cx = w / 2f
            label("%02d:%02d".format(Locale.US, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)), cx, h * 0.1f, h * 0.11f, DashWhite)
            val dayIdx = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val stripW = w * 0.19f
            DAYS.forEachIndexed { i, d ->
                val x = cx - stripW / 2 + stripW * (i + 0.5f) / 7f
                if (i == dayIdx) drawRoundRect(DashWhite, Offset(x - stripW / 16f, h * 0.175f), Size(stripW / 8f, h * 0.04f), CornerRadius(h * 0.006f))
                label(d, x, h * 0.195f, h * 0.018f, if (i == dayIdx) Color.Black else DashDim, face = medium)
            }
            label("$speed", cx, h * 0.38f, h * 0.17f, DashWhite)
            label(unit, cx, h * 0.5f, h * 0.04f, DashDim, face = plain)
            if (media.active && media.title.isNotBlank()) {
                label(media.title.take(16), cx, h * 0.6f, h * 0.04f, DashWhite, face = medium)
                if (media.artist.isNotBlank()) label(media.artist.take(20), cx, h * 0.66f, h * 0.03f, DashDim, face = plain)
            }

            // Mini gauges along the bottom centre
            val mini = listOf(
                Triple("COOLANT", DashChannel.COOLANT.fraction(obd), DashChannel.COOLANT.text(obd)),
                Triple("FUEL", DashChannel.FUEL.fraction(obd), DashChannel.FUEL.text(obd)),
                Triple("OUTSIDE", DashChannel.OUTSIDE.fraction(obd), DashChannel.OUTSIDE.text(obd)),
            )
            val mr = h * 0.065f
            mini.forEachIndexed { i, (name, frac, txt) ->
                miniGauge(Offset(cx + (i - 1) * mr * 2.5f, h * 0.84f), mr, frac, name, txt)
            }
        }
    }
}

private fun DrawScope.levelBar(rect: Rect, fraction: Float) {
    val segs = 16
    val gap = rect.height * 0.012f
    val segH = (rect.height - gap * (segs - 1)) / segs
    val lit = DashMath.segmentsLit(fraction, segs)
    val colours = listOf(Color(0xFF1E6BFF), Color(0xFF00C2FF), Color(0xFF20E070), Color(0xFFB8F02A), Color(0xFFFFE02A), Color(0xFFFF8A1E), Color(0xFFFF2A2A))
    for (i in 0 until segs) {
        val t = i / (segs - 1f) * (colours.size - 1)
        val a = colours[t.toInt().coerceAtMost(colours.size - 2)]
        val b = colours[(t.toInt() + 1).coerceAtMost(colours.size - 1)]
        val col = lerp(a, b, t - t.toInt())
        val y = rect.bottom - (i + 1) * segH - i * gap
        drawRect(if (i < lit) col else col.copy(alpha = 0.13f), Offset(rect.left, y), Size(rect.width, segH))
    }
}

private fun DrawScope.ticks(c: Offset, r: Float, start: Float, sweep: Float, n: Int, majorEvery: Int, color: Color) {
    for (i in 0..n) {
        val a = start + sweep * i / n
        val major = i % majorEvery == 0
        drawLine(color.copy(alpha = if (major) 1f else 0.6f), at(c, r, a), at(c, r * (if (major) 0.9f else 0.95f), a),
            strokeWidth = r * (if (major) 0.014f else 0.006f))
    }
}

private fun DrawScope.subDial(c: Offset, r: Float) {
    drawCircle(Color(0xFF0E0F11), r, c)
    drawCircle(Color(0xFF8A9099), r, c, style = Stroke(r * 0.05f))
}

private fun DrawScope.hand(c: Offset, len: Float, deg: Float, width: Float, color: Color) {
    drawLine(color, at(c, -len * 0.18f, deg), at(c, len, deg), strokeWidth = width, cap = StrokeCap.Round)
    drawCircle(color, width * 1.4f, c)
    drawCircle(Color(0xFF0B0C0E), width * 0.6f, c)
}

private const val TWIN_START = 120f
private const val TWIN_SWEEP = 300f

private fun DrawScope.speedCluster(c: Offset, r: Float, f: Float, max: Float, bearing: Float, rpmF: Float, hasRpm: Boolean, altM: Double, accent: Color) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF15171A), Color.Black), c, r), r, c)
    drawCircle(Color(0xFF6B7078), r, c, style = Stroke(r * 0.012f))
    val steps = (max / 2f).toInt()
    ticks(c, r * 0.97f, TWIN_START, TWIN_SWEEP, steps, 5, DashWhite)
    val numStep = if (max > 200f) 20 else 10
    var v = 0
    while (v <= max.toInt()) {
        val p = at(c, r * 0.78f, TWIN_START + TWIN_SWEEP * v / max)
        label("$v", p.x, p.y, r * 0.075f, DashWhite)
        v += numStep
    }
    // Sub-dials: rpm (top), compass (bottom-left), altitude (right)
    val rpmC = Offset(c.x, c.y - r * 0.34f)
    val sr = r * 0.22f
    subDial(rpmC, sr)
    for (k in 0..8) {
        val a = 135f + 270f * k / 8f
        drawLine(if (k >= 7) DashRed else DashWhite, at(rpmC, sr * 0.92f, a), at(rpmC, sr * 0.75f, a), strokeWidth = sr * 0.05f)
    }
    label("×1000", rpmC.x, rpmC.y + sr * 0.5f, sr * 0.2f, DashDim, face = plain)
    if (hasRpm) hand(rpmC, sr * 0.82f, 135f + 270f * rpmF, sr * 0.08f, accent)

    val compC = Offset(c.x - r * 0.4f, c.y + r * 0.18f)
    subDial(compC, sr)
    for ((i, l) in listOf("N", "E", "S", "W").withIndex()) {
        val p = at(compC, sr * 0.72f, -90f + 90f * i - bearing)
        label(l, p.x, p.y, sr * 0.24f, if (i == 0) DashRed else DashWhite)
    }
    rotate(-bearing, compC) {
        val tip = Path().apply {
            moveTo(compC.x, compC.y - sr * 0.55f); lineTo(compC.x + sr * 0.1f, compC.y); lineTo(compC.x - sr * 0.1f, compC.y); close()
        }
        val tail = Path().apply {
            moveTo(compC.x, compC.y + sr * 0.55f); lineTo(compC.x + sr * 0.1f, compC.y); lineTo(compC.x - sr * 0.1f, compC.y); close()
        }
        drawPath(tip, DashRed)
        drawPath(tail, Color(0xFF2A6BFF))
    }

    val altC = Offset(c.x + r * 0.4f, c.y + r * 0.18f)
    subDial(altC, sr)
    for (k in 0..10) drawLine(DashWhite, at(altC, sr * 0.92f, -90f + 36f * k), at(altC, sr * 0.8f, -90f + 36f * k), strokeWidth = sr * 0.04f)
    label("ALT", altC.x, altC.y - sr * 0.4f, sr * 0.2f, DashDim, face = plain)
    label("${altM.toInt()} m", altC.x, altC.y + sr * 0.45f, sr * 0.2f, DashWhite)
    hand(altC, sr * 0.8f, -90f + 360f * ((altM % 1000.0) / 1000.0).toFloat(), sr * 0.08f, accent)

    // Main speed needle: white blade with red edge
    val na = TWIN_START + TWIN_SWEEP * f
    val blade = r * 0.045f
    drawLine(DashRed, at(c, -r * 0.1f, na), at(c, r * 0.92f, na), strokeWidth = blade * 1.5f, cap = StrokeCap.Square)
    drawLine(DashWhite, at(c, -r * 0.08f, na), at(c, r * 0.9f, na), strokeWidth = blade, cap = StrokeCap.Square)
    drawCircle(Color(0xFF2A2C30), r * 0.05f, c)
    drawCircle(Color(0xFF8A9099), r * 0.05f, c, style = Stroke(r * 0.01f))
}

private fun DrawScope.watchFace(c: Offset, r: Float, now: Calendar, accent: Color) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF15171A), Color.Black), c, r), r, c)
    drawCircle(Color(0xFF6B7078), r, c, style = Stroke(r * 0.012f))
    ticks(c, r * 0.97f, -90f, 360f, 60, 5, DashWhite)
    for (hr in 1..12) {
        val p = at(c, r * 0.76f, -90f + hr * 30f)
        label("$hr", p.x, p.y, r * 0.13f, DashWhite)
    }
    // 24-hour inner ring
    for (hr in 2..24 step 2) {
        val p = at(c, r * 0.58f, -90f + hr * 15f)
        label("$hr", p.x, p.y, r * 0.05f, accent.copy(alpha = 0.85f), face = medium)
    }
    // Small seconds dial (left) and date window (right)
    val sc = Offset(c.x - r * 0.3f, c.y + r * 0.05f)
    val sr = r * 0.17f
    subDial(sc, sr)
    hand(sc, sr * 0.85f, -90f + now.get(Calendar.SECOND) * 6f, sr * 0.07f, accent)
    val db = Rect(Offset(c.x + r * 0.12f, c.y - r * 0.2f), Size(r * 0.42f, r * 0.42f))
    drawRoundRect(Color(0xFF0E0F11), db.topLeft, db.size, CornerRadius(r * 0.03f))
    drawRoundRect(Color(0xFF8A9099), db.topLeft, db.size, CornerRadius(r * 0.03f), style = Stroke(r * 0.008f))
    label("${now.get(Calendar.DAY_OF_MONTH)}", db.center.x, db.top + db.height * 0.22f, db.height * 0.24f, DashWhite)
    label(MONTHS[now.get(Calendar.MONTH)], db.center.x, db.top + db.height * 0.52f, db.height * 0.3f, DashWhite)
    label(DAYS[(now.get(Calendar.DAY_OF_WEEK) + 5) % 7], db.center.x, db.top + db.height * 0.82f, db.height * 0.2f, DashDim, face = medium)

    val (ha, ma, sa) = DashMath.handAngles(now.get(Calendar.HOUR), now.get(Calendar.MINUTE), now.get(Calendar.SECOND))
    // Hour and minute hands: white batons with dark centre line
    for ((deg, len, wid) in listOf(Triple(ha, r * 0.5f, r * 0.055f), Triple(ma, r * 0.82f, r * 0.04f))) {
        val a = deg - 90f
        drawLine(DashWhite, at(c, -r * 0.08f, a), at(c, len, a), strokeWidth = wid, cap = StrokeCap.Round)
        drawLine(Color(0xFF2A2C30), at(c, len * 0.25f, a), at(c, len * 0.9f, a), strokeWidth = wid * 0.35f, cap = StrokeCap.Round)
    }
    // Sweep seconds in gold with counterweight
    val s = sa - 90f
    drawLine(Gold, at(c, -r * 0.2f, s), at(c, r * 0.92f, s), strokeWidth = r * 0.012f, cap = StrokeCap.Round)
    drawCircle(Gold, r * 0.035f, c)
    drawCircle(Color(0xFF0B0C0E), r * 0.014f, c)
}

private fun DrawScope.miniGauge(c: Offset, r: Float, f: Float, name: String, value: String) {
    drawCircle(Color(0xFF15171A), r, c)
    drawCircle(Color(0xFF8A9099), r, c, style = Stroke(r * 0.05f))
    val tl = Offset(c.x - r * 0.8f, c.y - r * 0.8f)
    val sz = Size(r * 1.6f, r * 1.6f)
    drawArc(Color(0xFF3A3D42), 150f, 240f, false, tl, sz, style = Stroke(r * 0.12f))
    drawArc(DashRed, 330f, 60f, false, tl, sz, style = Stroke(r * 0.12f))
    val a = 150f + 240f * f
    drawLine(DashWhite, c, at(c, r * 0.75f, a), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
    drawCircle(DashWhite, r * 0.09f, c)
    label(value, c.x, c.y + r * 0.45f, r * 0.28f, DashWhite)
    label(name, c.x, c.y + r * 1.25f, r * 0.22f, DashDim, face = medium)
}
