package com.vizionconcepts.drive.ui

import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.translate
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
