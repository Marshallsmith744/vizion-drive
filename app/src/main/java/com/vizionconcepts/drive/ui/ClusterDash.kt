package com.vizionconcepts.drive.ui

import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vizionconcepts.drive.R
import com.vizionconcepts.drive.dashboard.DashMath
import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.vehicle.TripComputer
import java.text.SimpleDateFormat
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


/** Exo 2 italic (SIL OFL, bundled in res/font). */
private class ClusterFonts(ctx: Context) {
    val w500: Typeface = ctx.resources.getFont(R.font.exo2_500_italic)
    val w600: Typeface = ctx.resources.getFont(R.font.exo2_600_italic)
    val w700: Typeface = ctx.resources.getFont(R.font.exo2_700_italic)
    val w800: Typeface = ctx.resources.getFont(R.font.exo2_800_italic)
}

private val cPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val White = Color(0xFFF7F7F8)
private val Soft = Color(0xFFE9E9EC)

/** Simple original line icons on a 24-unit grid. */
private val ICONS = listOf(
    listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M12 7v5l3.2 2"),                                                       // time
    listOf("M5 18.5c3-6 10-2.5 14-12", "M3.5 18.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0", "M17.5 6.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0"), // trip
    listOf("M4 13a8 8 0 1 0 16 0a8 8 0 1 0-16 0", "M12 13V9", "M10 3h4M12 3v2", "M18.5 6.5l1.5-1.5"),                        // trip time
    listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M12 6.5l2.6 5.5-2.6 5.5-2.6-5.5z"),                                      // heading
    listOf("M9.5 4a1.5 1.5 0 0 1 3 0v8.6a3 3 0 1 1-3 0z", "M16 5l1.5-1.5M16.5 9h2.5M16 13l1.5 1.5"),                         // outside
    listOf("M3.5 17a8.5 8.5 0 1 1 17 0", "M12 17l3.5-5", "M3.5 17h17"),                                                     // average
    listOf("M12 20V5", "M6 11l6-6 6 6", "M5 20h14"),                                                                         // top speed
    listOf("M2 20l7-11 4 6 3-4 6 9z"),                                                                                       // altitude
)

private fun icon(ds: List<String>): List<Path> = ds.map { PathParser().parsePathString(it).toPath() }

private fun pt(c: Offset, r: Float, deg: Float) =
    Offset(c.x + r * cos(Math.toRadians(deg.toDouble())).toFloat(), c.y + r * sin(Math.toRadians(deg.toDouble())).toFloat())

private data class Tile(val name: String, val value: String, val unit: String, val sub: String)

/**
 * Speed cluster: one large analogue speedometer (chrome ring, glowing needle, speed ring, top-speed
 * marker) with a floating digital readout, flanked by eight tiles of data the head unit really has —
 * time, trip, trip time, heading, outside temperature, average speed, top speed and altitude.
 * Laid out on a 1774 × 887 design grid and scaled to the widget. Accents follow the cluster colour.
 */
@Composable
fun ClusterDash(ui: UiState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val fonts = remember { ClusterFonts(ctx) }
    val icons = remember { ICONS.map { icon(it) } }
    val now by produceState(Calendar.getInstance()) {
        while (true) { value = Calendar.getInstance(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    val g = ui.gps
    val mph = ui.settings.unit.name == "MPH"
    val su = if (mph) "mph" else "km/h"
    val speed = ui.speedShown
    val speedMax = if (mph) 180 else 280
    val speedA by animateFloatAsState(speed.toFloat().coerceIn(0f, speedMax.toFloat()), tween(220), label = "clSpeed")
    val top = ui.mpsShown(g.tripMaxMps)
    val acc = Color(ui.settings.clusterColor)
    val tripMin = (g.tripMovingMs / 60000L).toInt()
    val heading = if (g.hasFix) TripComputer.cardinal(g.bearing) else "--"
    val tiles = listOf(
        Tile("TIME", "%02d:%02d".format(Locale.US, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)), "",
            SimpleDateFormat("EEE MMM d", Locale.US).format(now.time).uppercase(Locale.US)),
        Tile("TRIP", String.format(Locale.US, "%.1f", ui.kmShown(g.tripKm)), if (mph) "mi" else "km", ""),
        Tile("TRIP TIME", "%d:%02d".format(Locale.US, tripMin / 60, tripMin % 60), "", ""),
        Tile("HEADING", heading, "", if (g.hasFix) "${g.bearing.toInt()}°" else "WAITING FOR GPS"),
        Tile("OUTSIDE", if (ui.weather.loaded) "${ui.weather.tempC.toInt()}" else "--", "°C",
            if (ui.weather.loaded) ui.weather.description.uppercase(Locale.US) else ""),
        Tile("AVG SPEED", "${ui.mpsShown(g.tripAvgMps)}", su, ""),
        Tile("TOP SPEED", "$top", su, ""),
        Tile("ALTITUDE", if (g.hasFix) "${g.altitudeM.toInt()}" else "--", "m", ""),
    )

    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val k = min(size.width / 1774f, size.height / 887f)
            val ox = (size.width - 1774f * k) / 2f
            val oy = (size.height - 887f * k) / 2f
            fun X(v: Float) = ox + v * k
            fun Y(v: Float) = oy + v * k
            fun K(v: Float) = v * k
            val accL = lerp(acc, Color.White, 0.35f)
            drawRect(Brush.radialGradient(listOf(Color(0xFF0E0E10), Color.Black), Offset(size.width / 2f, size.height / 2f), size.width * 0.6f))
            fun text(s: String, x: Float, y: Float, sz: Float, col: Color, face: Typeface, align: Paint.Align = Paint.Align.CENTER) {
                cPaint.textSize = sz; cPaint.color = col.toArgb(); cPaint.typeface = face; cPaint.textAlign = align
                drawContext.canvas.nativeCanvas.drawText(s, x, y + sz * 0.36f, cPaint)
            }

            // ── Tiles: four a side, tucking in behind the ring ──
            tiles.forEachIndexed { i, t ->
                val left = i < 4
                val y = Y(60f + (i % 4) * 195f)
                val x = if (left) X(40f) else X(1174f)
                val w = K(560f)
                val h = K(165f)
                val c0 = if (left) X(70f) else X(1300f)
                val cr = CornerRadius(K(20f))
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1D1D20), Color(0xFF0B0B0C)), y, y + h), Offset(x, y), Size(w, h), cr)
                drawRoundRect(Color(0xFF2E2F33), Offset(x, y), Size(w, h), cr, style = Stroke(K(2f)))
                drawLine(Color.White.copy(alpha = 0.12f), Offset(x + K(20f), y + K(1.5f)), Offset(x + w - K(20f), y + K(1.5f)), strokeWidth = K(2f))
                val sc = K(64f) / 24f
                withTransform({ translate(c0, y + K(40f)); scale(sc, sc, Offset.Zero) }) {
                    for (p in icons[i]) drawPath(p, acc, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                val tx = c0 + K(95f)
                text(t.name, tx, y + K(42f), K(28f), Color(0xFFBFC0C6), fonts.w500, Paint.Align.LEFT)
                drawRect(acc, Offset(tx, y + K(70f)), Size(K(70f), K(3f)))
                cPaint.typeface = fonts.w700; cPaint.textSize = K(66f)
                val vw = cPaint.measureText(t.value)
                text(t.value, tx, y + K(105f), K(66f), White, fonts.w700, Paint.Align.LEFT)
                if (t.unit.isNotEmpty()) text(t.unit, tx + vw + K(6f), y + K(115f), K(32f), Color(0xFFE0E0E4), fonts.w700, Paint.Align.LEFT)
                if (t.sub.isNotEmpty()) text(t.sub, tx, y + K(146f), K(22f), Color(0xFF8F9096), fonts.w500, Paint.Align.LEFT)
            }

            // ── Big speedometer ──
            val c = Offset(X(887f), Y(452f))
            val r = K(392f)
            val st = 135f
            val sw = 270f
            val mx = speedMax.toFloat()
            fun ang(v: Float) = st + sw * v / mx
            fun arc(rad: Float, a0: Float, swp: Float, col: Color, width: Float, round: Boolean = false) {
                if (swp <= 0.01f) return
                drawArc(col, a0, swp, false, Offset(c.x - rad, c.y - rad), Size(rad * 2, rad * 2), style = Stroke(width, cap = if (round) StrokeCap.Round else StrokeCap.Butt))
            }
            drawCircle(Brush.radialGradient(0.85f to Color.Black.copy(alpha = 0.7f), 1f to Color.Transparent, center = c, radius = r + K(40f)), r + K(40f), c)
            arc(r - K(4f), 20f, 140f, acc.copy(alpha = 0.1f), K(80f))
            arc(r - K(4f), 20f, 140f, acc.copy(alpha = 0.22f), K(40f))
            drawCircle(Brush.radialGradient(0f to Color(0xFF141417), 0.8f to Color(0xFF050506), 1f to Color.Black, center = Offset(c.x, c.y - r * 0.1f), radius = r * 1.1f), r, c)
            drawCircle(
                Brush.linearGradient(
                    0f to Color(0xFFF2F3F5), 0.25f to Color(0xFF8E9299), 0.5f to Color(0xFFE4E6E9), 0.75f to Color(0xFF55595F), 1f to Color(0xFFC9CCD1),
                    start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
                ),
                r - K(10f), c, style = Stroke(K(20f)),
            )
            drawCircle(Color(0xFF2A2B2E), r, c, style = Stroke(K(2f)))
            drawCircle(Color.Black, r - K(20f), c, style = Stroke(K(3f)))
            arc(r - K(24f), 20f, 140f, acc.copy(alpha = 0.75f), K(4f))
            for (v in 0..speedMax step 2) {
                val major = v % 20 == 0
                val half = v % 10 == 0
                drawLine(Color(0xFFE8E8EA), pt(c, r - K(32f), ang(v.toFloat())), pt(c, r - K(if (major) 76f else if (half) 54f else 44f), ang(v.toFloat())),
                    strokeWidth = K(if (major) 6f else if (half) 2.8f else 1.4f))
            }
            for (v in 0..speedMax step 20) {
                val p = pt(c, r - K(122f), ang(v.toFloat()))
                text("$v", p.x, p.y, K(44f), White, fonts.w700)
            }
            // Digital-style speed ring
            val pr = r - K(168f)
            val frac = (speedA / mx).coerceIn(0f, 1f)
            arc(pr, st, sw, Color(0xFF1C1D20), K(10f))
            arc(pr, st, sw * frac, acc.copy(alpha = 0.18f), K(34f), true)
            arc(pr, st, sw * frac, acc.copy(alpha = 0.35f), K(18f), true)
            arc(pr, st, sw * frac, acc, K(8f), true)
            // Top-speed marker for this trip
            if (top > 0) {
                val a = ang(top.toFloat().coerceAtMost(mx))
                val tip = pt(c, r - K(30f), a)
                val b1 = pt(c, r - K(4f), a - 1.8f)
                val b2 = pt(c, r - K(4f), a + 1.8f)
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }, accL)
            }
            text(if (g.hasFix) "$heading  ·  ${g.bearing.toInt()}°" else "WAITING FOR GPS", c.x, c.y - K(150f), K(30f), Color(0xFFCFCFD4), fonts.w500)
            // Hub and needle
            drawCircle(Color(0xFF080809), K(64f), c)
            drawCircle(Color(0xFF2C2D31), K(64f), c, style = Stroke(K(2f)))
            val na = ang(speedA)
            val n0 = pt(c, K(58f), na)
            val n1 = pt(c, r - K(40f), na)
            drawLine(acc.copy(alpha = 0.12f), n0, n1, strokeWidth = K(34f), cap = StrokeCap.Round)
            drawLine(acc.copy(alpha = 0.3f), n0, n1, strokeWidth = K(18f), cap = StrokeCap.Round)
            drawLine(acc, n0, n1, strokeWidth = K(8f), cap = StrokeCap.Round)
            drawLine(accL.copy(alpha = 0.8f), n0, n1, strokeWidth = K(2.4f), cap = StrokeCap.Round)
            // Floating digital speed box, over the needle
            val bw = K(296f)
            val bh = K(168f)
            val bTl = Offset(c.x - bw / 2f, c.y + K(46f))
            val bCr = CornerRadius(K(20f))
            drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(bTl.x + K(5f), bTl.y + K(10f)), Size(bw, bh), bCr)
            drawRoundRect(acc.copy(alpha = 0.18f), Offset(bTl.x - K(5f), bTl.y - K(5f)), Size(bw + K(10f), bh + K(10f)), CornerRadius(K(24f)))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF17171A), Color(0xFF060607)), bTl.y, bTl.y + bh), bTl, Size(bw, bh), bCr)
            drawRoundRect(acc.copy(alpha = 0.85f), bTl, Size(bw, bh), bCr, style = Stroke(K(2.5f)))
            drawLine(Color.White.copy(alpha = 0.1f), Offset(bTl.x + K(20f), bTl.y + K(2.5f)), Offset(bTl.x + bw - K(20f), bTl.y + K(2.5f)), strokeWidth = K(2f))
            text("$speed", c.x + K(5f), bTl.y + K(70f), K(122f), Color.White, fonts.w800)
            text(su, c.x, bTl.y + K(142f), K(30f), Color(0xFFEDEDF0), fonts.w500)
        }
    }
}
