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
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Twin-gauge cluster: tachometer left, speedometer right (chrome rings, glowing needles),
 * four bar tiles in the middle (coolant, oil temp, voltage, fuel), a header with time and
 * outside temp and an odometer / trip strip along the bottom.
 * Everything is laid out on a 1774 × 887 design grid and scaled to the widget, so it keeps its shape
 * at any size. Every accent follows the cluster colour (Settings → Digital Dash).
 */

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
private val ICON_COOLANT = listOf(
    "M10.5 4a1.5 1.5 0 0 1 3 0v8.6a3 3 0 1 1-3 0z", "M15 6.5h2.5M15 9.5h2.5",
    "M2.5 20.5c1.5-1 3 1 4.5 0s3 1 4.5 0 3 1 4.5 0 3 1 4.5 0",
)
private val ICON_OIL = listOf(
    "M3 11.5h3l2 1.5h8.5l4.5-3 .8.8-5.3 5.7H6.5z", "M9 13V9.5h3V13M8 9.5h5",
    "M21.5 15.2c0 .9-.5 1.6-1.1 1.6s-1.1-.7-1.1-1.6.6-1.6 1.1-2.3c.5.7 1.1 1.4 1.1 2.3z",
)
private val ICON_BATTERY = listOf("M3 8.5h18v11H3z", "M6 8.5V6.5h3v2M15 8.5V6.5h3v2", "M5.8 14h3.4M7.5 12.3v3.4M14.8 14h3.4")
private val ICON_FUEL = listOf(
    "M5 20V5.5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1V20", "M3.5 20h12", "M7 7.5h5v4H7z",
    "M14 10.5h2a1 1 0 0 1 1 1v5.5a1.5 1.5 0 0 0 3 0V9.5l-3-3",
)

private fun icon(ds: List<String>): List<Path> = ds.map { PathParser().parsePathString(it).toPath() }

private fun pt(c: Offset, r: Float, deg: Float) =
    Offset(c.x + r * cos(Math.toRadians(deg.toDouble())).toFloat(), c.y + r * sin(Math.toRadians(deg.toDouble())).toFloat())

@Composable
fun ClusterDash(ui: UiState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val fonts = remember { ClusterFonts(ctx) }
    val icons = remember { listOf(icon(ICON_COOLANT), icon(ICON_OIL), icon(ICON_BATTERY), icon(ICON_FUEL)) }
    val now by produceState(Calendar.getInstance()) {
        while (true) { value = Calendar.getInstance(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    val obd = ui.obd
    val rpm = obd[ObdPid.RPM]
    val rpmK by animateFloatAsState((rpm ?: 0f) / 1000f, tween(180), label = "clRpm")
    val speed = ui.speedShown
    val mph = ui.settings.unit.name == "MPH"
    val speedMax = if (mph) 180 else 280
    val speedA by animateFloatAsState(speed.toFloat().coerceIn(0f, speedMax.toFloat()), tween(220), label = "clSpeed")
    val acc = Color(ui.settings.clusterColor)
    val outside = obd[ObdPid.AMBIENT]?.toInt() ?: if (ui.weather.loaded) ui.weather.tempC.toInt() else null
    val coolant = obd[ObdPid.COOLANT]
    val oil = obd[ObdPid.OIL_TEMP]
    val volt = obd[ObdPid.VOLTAGE]
    val fuel = obd[ObdPid.FUEL_LEVEL]
    val tiles = listOf(
        Tile("COOLANT", coolant?.let { "${it.toInt()}" } ?: "--", "°C", coolant?.let { DashMath.fraction(it, 40f, 120f) } ?: 0f),
        Tile("OIL TEMP", oil?.let { "${it.toInt()}" } ?: "--", "°C", oil?.let { DashMath.fraction(it, 40f, 150f) } ?: 0f),
        Tile("VOLTAGE", volt?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "V", volt?.let { DashMath.fraction(it, 11f, 15f) } ?: 0f),
        Tile("FUEL", fuel?.let { "${it.toInt()}" } ?: "--", "%", fuel?.let { it / 100f } ?: 0f),
    )
    val distUnit = if (mph) "mi" else "km"
    val trip = String.format(Locale.US, "%.1f %s", ui.kmShown(ui.gps.tripKm), distUnit)
    val time = "${now.get(Calendar.HOUR_OF_DAY)}:%02d".format(Locale.US, now.get(Calendar.MINUTE))
    val redK = min(8.5f, ui.settings.shiftRpm / 1000f + 0.6f)

    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val k = min(size.width / 1774f, size.height / 887f)
            val ox = (size.width - 1774f * k) / 2f
            val oy = (size.height - 887f * k) / 2f
            fun X(v: Float) = ox + v * k
            fun Y(v: Float) = oy + v * k
            fun K(v: Float) = v * k
            val accD = lerp(acc, Color.Black, 0.45f)
            val accL = lerp(acc, Color.White, 0.35f)
            drawRect(Brush.radialGradient(listOf(Color(0xFF0E0E10), Color.Black), Offset(size.width / 2f, size.height * 0.45f), size.width * 0.6f))

            fun text(s: String, x: Float, y: Float, sz: Float, col: Color, face: Typeface, align: Paint.Align = Paint.Align.CENTER) {
                cPaint.textSize = sz; cPaint.color = col.toArgb(); cPaint.typeface = face; cPaint.textAlign = align
                drawContext.canvas.nativeCanvas.drawText(s, x, y + sz * 0.36f, cPaint)
            }
            fun glowPath(p: Path, col: Color, w: Float) {
                drawPath(p, col.copy(alpha = 0.12f), style = Stroke(w * 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p, col.copy(alpha = 0.25f), style = Stroke(w * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p, col, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            fun fadeLine(x0: Float, x1: Float, y: Float, th: Float, alpha: Float = 1f) {
                val b = Brush.horizontalGradient(0f to Color.Transparent, 0.5f to acc.copy(alpha = alpha), 1f to Color.Transparent, startX = x0, endX = x1)
                drawRect(b, Offset(x0, y - th / 2f), Size(x1 - x0, th))
            }

            // ── Header ──
            val hdr = Path().apply {
                moveTo(X(330f), Y(26f)); lineTo(X(1444f), Y(26f)); lineTo(X(1380f), Y(40f))
                cubicTo(X(1300f), Y(58f), X(1250f), Y(140f), X(1150f), Y(142f)); lineTo(X(624f), Y(142f))
                cubicTo(X(524f), Y(140f), X(474f), Y(58f), X(394f), Y(40f)); close()
            }
            drawPath(hdr, Brush.verticalGradient(listOf(Color(0xFF151517), Color(0xFF060607)), Y(26f), Y(142f)))
            glowPath(hdr, acc, K(2.2f))
            text(time, X(630f), Y(68f), K(44f), Color(0xFFF2F2F4), fonts.w500)
            text(outside?.let { "$it°C" } ?: "--°C", X(1165f), Y(68f), K(44f), Color(0xFFF2F2F4), fonts.w500)

            // ── Bottom glow and odometer strip ──
            fadeLine(X(520f), X(1255f), Y(734f), K(10f), 0.35f)
            fadeLine(X(560f), X(1215f), Y(734f), K(3f))
            val band = Path().apply { moveTo(X(150f), Y(860f)); lineTo(X(225f), Y(778f)); lineTo(X(1550f), Y(778f)); lineTo(X(1625f), Y(860f)); close() }
            drawPath(band, Color(0xFF070708))
            val leftEdge = Path().apply { moveTo(X(60f), Y(860f)); lineTo(X(140f), Y(778f)); lineTo(X(560f), Y(778f)) }
            val rightEdge = Path().apply { moveTo(X(1714f), Y(860f)); lineTo(X(1634f), Y(778f)); lineTo(X(1214f), Y(778f)) }
            val edgeL = Brush.horizontalGradient(0f to Color.Transparent, 0.4f to acc, 1f to Color.Transparent, startX = X(60f), endX = X(560f))
            val edgeR = Brush.horizontalGradient(0f to Color.Transparent, 0.6f to acc, 1f to Color.Transparent, startX = X(1214f), endX = X(1714f))
            drawPath(leftEdge, edgeL, style = Stroke(K(2.5f)))
            drawPath(rightEdge, edgeR, style = Stroke(K(2.5f)))
            fadeLine(X(80f), X(1694f), Y(859f), K(3f), 0.8f)
            text("ODO", X(420f), Y(812f), K(32f), Soft, fonts.w500)
            text("--- $distUnit", X(640f), Y(812f), K(46f), White, fonts.w600)
            drawLine(Color(0xFFBBBBC0), Offset(X(905f), Y(785f)), Offset(X(885f), Y(840f)), strokeWidth = K(2f))
            text("TRIP A", X(1060f), Y(812f), K(32f), Soft, fonts.w500)
            text(trip, X(1295f), Y(812f), K(46f), White, fonts.w600)

            // ── Centre tiles: wide panels whose ends tuck in behind both gauge rings ──
            tiles.forEachIndexed { i, t ->
                val x = X(560f)
                val y = Y(200f + i * 111.7f)
                val w = K(654f)
                val h = K(95f)
                val cr = CornerRadius(K(18f))
                val l = X(708f)   // content stays inside the visible gap between the rings
                val r = X(1060f)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1D1D20), Color(0xFF0B0B0C)), y, y + h), Offset(x, y), Size(w, h), cr)
                drawRoundRect(Color(0xFF2E2F33), Offset(x, y), Size(w, h), cr, style = Stroke(K(2f)))
                drawLine(Color.White.copy(alpha = 0.12f), Offset(x + K(18f), y + K(1.5f)), Offset(x + w - K(18f), y + K(1.5f)), strokeWidth = K(2f))
                val sc = K(52f) / 24f
                withTransform({ translate(l, y + K(22f)); scale(sc, sc, Offset.Zero) }) {
                    for (p in icons[i]) drawPath(p, Color(0xFFEDEDF0), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                text(t.name, l + K(72f), y + K(37f), K(26f), Color(0xFFEDEDF0), fonts.w500, Paint.Align.LEFT)
                cPaint.typeface = fonts.w600; cPaint.textSize = K(26f)
                val uw = cPaint.measureText(t.unit)
                text(t.unit, r, y + K(46f), K(26f), White, fonts.w600, Paint.Align.RIGHT)
                text(t.value, r - K(4f) - uw, y + K(40f), K(44f), White, fonts.w600, Paint.Align.RIGHT)
                val bx = l + K(70f)
                val bw = r - bx
                val by = y + K(68f)
                drawRoundRect(Color(0xFF26272A), Offset(bx, by), Size(bw, K(8f)), CornerRadius(K(4f)))
                if (t.fraction > 0f) {
                    val fw = bw * t.fraction
                    drawRoundRect(acc.copy(alpha = 0.3f), Offset(bx, by - K(3f)), Size(fw, K(14f)), CornerRadius(K(7f)))
                    drawRoundRect(
                        Brush.horizontalGradient(0f to accD, 0.7f to acc, 1f to accL, startX = bx, endX = bx + fw),
                        Offset(bx, by), Size(fw, K(8f)), CornerRadius(K(4f)),
                    )
                }
            }

            // ── Gauge shell (shared) ──
            fun shell(c: Offset, glowStart: Float, glowSweep: Float) {
                val r = K(338f)
                val gr = r - K(4f)
                for ((mul, a) in listOf(5f to 0.10f, 2.5f to 0.22f)) {
                    drawArc(acc.copy(alpha = a), glowStart, glowSweep, false, Offset(c.x - gr, c.y - gr), Size(gr * 2, gr * 2), style = Stroke(K(6f) * mul))
                }
                drawCircle(Brush.radialGradient(0f to Color(0xFF121214), 0.8f to Color(0xFF050506), 1f to Color.Black, center = Offset(c.x, c.y - r * 0.1f), radius = r * 1.1f), r, c)
                drawCircle(
                    Brush.linearGradient(
                        0f to Color(0xFFF2F3F5), 0.25f to Color(0xFF8E9299), 0.5f to Color(0xFFE4E6E9), 0.75f to Color(0xFF55595F), 1f to Color(0xFFC9CCD1),
                        start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
                    ),
                    r - K(9f), c, style = Stroke(K(18f)),
                )
                drawCircle(Color(0xFF2A2B2E), r, c, style = Stroke(K(2f)))
                drawCircle(Color.Black, r - K(18f), c, style = Stroke(K(3f)))
                val ir = r - K(22f)
                drawArc(acc.copy(alpha = 0.7f), glowStart, glowSweep, false, Offset(c.x - ir, c.y - ir), Size(ir * 2, ir * 2), style = Stroke(K(4f)))
                drawCircle(Color(0xFF2C2D31), K(165f), c, style = Stroke(K(2f)))
            }
            fun tick(c: Offset, a: Float, r0: Float, r1: Float, col: Color, w: Float) = drawLine(col, pt(c, r0, a), pt(c, r1, a), strokeWidth = w)
            fun needle(c: Offset, a: Float, r0: Float, r1: Float) {
                val p0 = pt(c, r0, a)
                val p1 = pt(c, r1, a)
                drawLine(acc.copy(alpha = 0.12f), p0, p1, strokeWidth = K(30f), cap = StrokeCap.Round)
                drawLine(acc.copy(alpha = 0.3f), p0, p1, strokeWidth = K(16f), cap = StrokeCap.Round)
                drawLine(acc, p0, p1, strokeWidth = K(7f), cap = StrokeCap.Round)
                drawLine(accL.copy(alpha = 0.8f), p0, p1, strokeWidth = K(2f), cap = StrokeCap.Round)
            }

            // ── Tachometer ──
            run {
                val c = Offset(X(355f), Y(420f))
                val st = 140f
                val sw = 243f
                val mx = 9f
                fun ang(v: Float) = st + sw * v / mx
                shell(c, -20f, 120f)
                val zr = K(292f)
                drawArc(acc, ang(redK), ang(mx) - ang(redK), false, Offset(c.x - zr, c.y - zr), Size(zr * 2, zr * 2), style = Stroke(K(26f)))
                drawArc(accL.copy(alpha = 0.5f), ang(redK), ang(mx) - ang(redK), false, Offset(c.x - zr, c.y - zr), Size(zr * 2, zr * 2), style = Stroke(K(6f)))
                for (v in 0..90) {
                    val major = v % 10 == 0
                    val half = v % 5 == 0
                    tick(c, ang(v / 10f), K(306f), K(if (major) 272f else if (half) 288f else 296f),
                        if (v / 10f >= redK) Color(0xFF1A0002) else Color(0xFFE8E8EA), K(if (major) 6f else if (half) 3f else 1.6f))
                }
                for (n in 0..9) {
                    val p = pt(c, K(228f), ang(n.toFloat()))
                    text("$n", p.x, p.y, K(68f), if (n >= redK + 1.4f) acc else White, fonts.w700)
                }
                text("RPM x1000", c.x + K(7f), c.y + K(108f), K(28f), Soft, fonts.w500)
                val gear = Path().apply {
                    moveTo(c.x - K(80f), c.y + K(155f)); lineTo(c.x + K(80f), c.y + K(155f))
                    lineTo(c.x + K(115f), c.y + K(255f)); lineTo(c.x - K(105f), c.y + K(255f)); close()
                }
                drawPath(gear, Color(0xFF070708))
                drawPath(gear, Color(0xFF3A3B3F), style = Stroke(K(2f), join = StrokeJoin.Round))
                text("–", c.x + K(5f), c.y + K(205f), K(86f), White, fonts.w800)
                drawCircle(Color(0xFF080809), K(62f), c)
                drawCircle(Color(0xFF2C2D31), K(62f), c, style = Stroke(K(2f)))
                needle(c, ang(rpmK.coerceIn(0f, mx)), K(55f), K(272f))
            }

            // ── Speedometer ──
            run {
                val c = Offset(X(1410f), Y(420f))
                val st = 155f
                val sw = 260f
                val mx = speedMax.toFloat()
                fun ang(v: Float) = st + sw * v / mx
                shell(c, 80f, 110f)
                for (v in 0..speedMax step 2) {
                    val major = v % 20 == 0
                    val half = v % 10 == 0
                    tick(c, ang(v.toFloat()), K(306f), K(if (major) 272f else if (half) 290f else 298f), Color(0xFFE8E8EA),
                        K(if (major) 6f else if (half) 2.6f else 1.3f))
                }
                for (v in 0..speedMax step 20) {
                    val p = pt(c, K(230f), ang(v.toFloat()))
                    text("$v", p.x, p.y, K(39f), White, fonts.w700)
                }
                drawCircle(Color(0xFF080809), K(55f), c)
                drawCircle(Color(0xFF2C2D31), K(55f), c, style = Stroke(K(2f)))
                needle(c, ang(speedA), K(50f), K(275f))
                // Floating digital speed box, drawn over the needle and clear of the hub
                val bTl = Offset(c.x - K(128f), c.y + K(64f))
                val bSz = Size(K(210f), K(124f))
                val bCr = CornerRadius(K(16f))
                drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(bTl.x + K(4f), bTl.y + K(8f)), bSz, bCr)
                drawRoundRect(acc.copy(alpha = 0.18f), Offset(bTl.x - K(4f), bTl.y - K(4f)), Size(bSz.width + K(8f), bSz.height + K(8f)), CornerRadius(K(20f)))
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF17171A), Color(0xFF060607)), bTl.y, bTl.y + bSz.height), bTl, bSz, bCr)
                drawRoundRect(acc.copy(alpha = 0.8f), bTl, bSz, bCr, style = Stroke(K(2f)))
                drawLine(Color.White.copy(alpha = 0.1f), Offset(bTl.x + K(16f), bTl.y + K(2f)), Offset(bTl.x + bSz.width - K(16f), bTl.y + K(2f)), strokeWidth = K(2f))
                text("$speed", bTl.x + bSz.width / 2f + K(3f), bTl.y + K(48f), K(88f), Color.White, fonts.w800)
                text(if (mph) "mph" else "km/h", bTl.x + bSz.width / 2f, bTl.y + K(104f), K(26f), Color(0xFFEDEDF0), fonts.w500)
            }

        }
    }
}

private data class Tile(val name: String, val value: String, val unit: String, val fraction: Float)
