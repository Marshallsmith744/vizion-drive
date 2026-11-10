package com.vizionconcepts.drive.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vizionconcepts.drive.settings.GaugeStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Instrument-cluster style dials: Sport glow, Chrome classic, Race band, Road view. */
fun GaugeStyle.isPremium() = this == GaugeStyle.SPORT_GLOW || this == GaugeStyle.CHROME ||
    this == GaugeStyle.RACE_BAND || this == GaugeStyle.ROAD

private val White = Color(0xFFF4F6F8)
private val Face = Color(0xFF0B0C0E)

@Composable
fun PremiumDial(
    style: GaugeStyle,
    value: Float,
    max: Float,
    text: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    redFrom: Float? = null,
    gear: String? = null,
) {
    val anim by animateFloatAsState((value / max).coerceIn(0f, 1f), tween(220), label = "dial")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = min(maxWidth.value, maxHeight.value)
        val density = LocalDensity.current
        fun sp(f: Float) = with(density) { (d * f).dp.toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2f * 0.96f
            val c = center
            when (style) {
                GaugeStyle.ROAD -> drawRoad(c, r, anim, max, color, gear != null)
                else -> drawClassicDial(style, c, r, anim, max, color, redFrom)
            }
        }
        val numberColor = if (redFrom != null && value >= redFrom) Color(0xFFFF453A) else White
        when (style) {
            GaugeStyle.CHROME -> {
                // Digital readout lives in the lower box; "km/h" sits above the hub.
                Text(unit, color = White.copy(alpha = 0.8f), fontSize = sp(0.055f), fontStyle = FontStyle.Italic,
                    modifier = Modifier.offset(y = (-d * 0.2f).dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (d * 0.33f).dp)) {
                    Text(text, color = numberColor, fontSize = sp(0.13f), fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic, lineHeight = sp(0.13f))
                    Text(unit, color = White.copy(alpha = 0.7f), fontSize = sp(0.04f), fontStyle = FontStyle.Italic)
                }
                if (gear != null) Text(gear, color = White, fontSize = sp(0.07f), fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = (d * 0.2f).dp, y = (d * 0.31f).dp))
            }
            GaugeStyle.ROAD -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-d * 0.03f).dp)) {
                    Text(text, color = numberColor, fontSize = sp(0.2f), fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic, lineHeight = sp(0.2f))
                    Text(unit, color = White.copy(alpha = 0.85f), fontSize = sp(0.055f), fontStyle = FontStyle.Italic)
                }
                if (gear != null) Text(gear, color = White, fontSize = sp(0.07f), fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (d * 0.42f).dp))
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (d * 0.04f).dp)) {
                    Text(text, color = numberColor, fontSize = sp(0.19f), fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic, lineHeight = sp(0.19f))
                    Text(unit, color = White.copy(alpha = 0.85f), fontSize = sp(0.055f), fontStyle = FontStyle.Italic)
                }
                if (gear != null) Text(gear, color = White, fontSize = sp(0.08f), fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (d * 0.38f).dp))
            }
        }
    }
}

private const val START = 135f
private const val SWEEP = 270f

private fun polar(c: Offset, r: Float, deg: Float) =
    Offset(c.x + r * cos(Math.toRadians(deg.toDouble())).toFloat(), c.y + r * sin(Math.toRadians(deg.toDouble())).toFloat())

/** Soft glow without blur filters: the same stroke drawn wider and fainter a few times. */
private fun DrawScope.glowArc(color: Color, c: Offset, r: Float, start: Float, sweep: Float, width: Float) {
    val tl = Offset(c.x - r, c.y - r)
    val s = Size(r * 2, r * 2)
    for ((mul, a) in listOf(5f to 0.06f, 3.2f to 0.12f, 2f to 0.25f)) {
        drawArc(color.copy(alpha = a), start, sweep, false, tl, s, style = Stroke(width * mul, cap = StrokeCap.Round))
    }
    drawArc(color, start, sweep, false, tl, s, style = Stroke(width, cap = StrokeCap.Round))
}

private fun DrawScope.chromeRing(c: Offset, r: Float, w: Float) {
    drawCircle(
        Brush.sweepGradient(
            listOf(Color(0xFF6B7078), Color(0xFFF2F4F6), Color(0xFF5A5F66), Color(0xFFD9DCE0), Color(0xFF4A4E54), Color(0xFFEDEFF2), Color(0xFF6B7078)),
            c,
        ),
        r, c, style = Stroke(w),
    )
}

private fun DrawScope.carbon(c: Offset, r: Float) {
    val clip = Path().apply { addOval(Rect(c, r)) }
    clipPath(clip) {
        drawCircle(Color(0xFF101114), r, c)
        val step = r / 9f
        var x = -r * 2
        while (x < r * 2) {
            drawLine(Color.White.copy(alpha = 0.035f), Offset(c.x + x, c.y - r), Offset(c.x + x + r * 2, c.y + r), strokeWidth = step * 0.45f)
            drawLine(Color.Black.copy(alpha = 0.25f), Offset(c.x + x + r * 2, c.y - r), Offset(c.x + x, c.y + r), strokeWidth = step * 0.2f)
            x += step
        }
    }
}

private fun scaleSteps(max: Float): Pair<Float, Int> =
    if (max <= 10f) 1f to 4 else if (max <= 180f) 20f to 4 else 20f to 4

private fun DrawScope.drawClassicDial(style: GaugeStyle, c: Offset, r: Float, f: Float, max: Float, color: Color, redFrom: Float?) {
    // Bezel + face
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1C20), Face), c, r), r, c)
    when (style) {
        GaugeStyle.CHROME -> chromeRing(c, r * 0.97f, r * 0.045f)
        GaugeStyle.SPORT_GLOW -> {
            chromeRing(c, r * 0.985f, r * 0.02f)
            glowArc(color, c, r * 0.93f, START, SWEEP, r * 0.018f)
        }
        GaugeStyle.RACE_BAND -> {
            chromeRing(c, r * 0.985f, r * 0.015f)
            // Segmented band lights up with the value.
            val segs = 36
            val gap = 1.6f
            val each = SWEEP / segs
            for (i in 0 until segs) {
                val lit = (i + 1f) / segs <= f + 0.0001f
                val a0 = START + i * each + gap / 2
                drawArc(
                    if (lit) color else color.copy(alpha = 0.16f), a0, each - gap, false,
                    Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.085f),
                )
            }
        }
        else -> Unit
    }

    // Red zone
    redFrom?.let {
        val rf = (it / max).coerceIn(0f, 1f)
        drawArc(Color(0xFFFF3B30).copy(alpha = 0.7f), START + SWEEP * rf, SWEEP * (1 - rf), false,
            Offset(c.x - r * 0.83f, c.y - r * 0.83f), Size(r * 1.66f, r * 1.66f), style = Stroke(r * 0.03f))
    }

    // Ticks + numerals
    val (major, minors) = scaleSteps(max)
    val tickOuter = if (style == GaugeStyle.RACE_BAND) r * 0.82f else r * 0.88f
    val numeralColor = when (style) {
        GaugeStyle.CHROME -> if (color.red > 0.8f && color.green > 0.5f && color.blue < 0.4f) color else White
        else -> White
    }
    val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = r * 0.1f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        this.color = numeralColor.toArgb()
    }
    val totalMinor = ((max / major) * minors).toInt()
    for (i in 0..totalMinor) {
        val deg = START + SWEEP * i / totalMinor
        val isMajor = i % minors == 0
        val len = if (isMajor) r * 0.1f else r * 0.045f
        val tickColor = if (style == GaugeStyle.SPORT_GLOW && isMajor) color else if (isMajor) White else White.copy(alpha = 0.7f)
        drawLine(tickColor, polar(c, tickOuter - len, deg), polar(c, tickOuter, deg),
            strokeWidth = if (isMajor) r * 0.018f else r * 0.008f)
        if (isMajor) {
            val p = polar(c, tickOuter - r * 0.2f, deg)
            val label = (major * (i / minors)).toInt().toString()
            drawContext.canvas.nativeCanvas.drawText(label, p.x, p.y + paint.textSize / 3f, paint)
        }
    }

    // Centre: carbon disc with a coloured inner ring (sport/band) or a plain hub (chrome)
    if (style == GaugeStyle.CHROME) {
        drawCircle(Color(0xFF15171A), r * 0.42f, c)
        drawCircle(Color.White.copy(alpha = 0.12f), r * 0.42f, c, style = Stroke(r * 0.008f))
    } else {
        carbon(c, r * 0.48f)
        drawCircle(color.copy(alpha = 0.35f), r * 0.48f, c, style = Stroke(r * 0.012f))
    }

    // Gear / readout box at the bottom
    val boxTop = c.y + r * 0.6f
    val trap = Path().apply {
        moveTo(c.x - r * 0.42f, boxTop)
        lineTo(c.x + r * 0.42f, boxTop)
        lineTo(c.x + r * 0.5f, c.y + r * 0.86f)
        lineTo(c.x - r * 0.5f, c.y + r * 0.86f)
        close()
    }
    drawPath(trap, Color(0xFF15171A))
    drawPath(trap, if (style == GaugeStyle.CHROME) Color.White.copy(alpha = 0.25f) else color.copy(alpha = 0.6f), style = Stroke(r * 0.01f))

    // Needle (drawn last so it sits over everything except the readout)
    val deg = START + SWEEP * f
    val tip = polar(c, r * 0.8f, deg)
    val baseL = polar(c, r * 0.03f, deg - 90)
    val baseR = polar(c, r * 0.03f, deg + 90)
    val tail = polar(c, -r * 0.12f, deg)
    val needleColor = if (style == GaugeStyle.SPORT_GLOW || style == GaugeStyle.ROAD) color else Color(0xFFFF2D1F)
    val needle = Path().apply { moveTo(tip.x, tip.y); lineTo(baseL.x, baseL.y); lineTo(tail.x, tail.y); lineTo(baseR.x, baseR.y); close() }
    drawLine(needleColor.copy(alpha = 0.25f), c, tip, strokeWidth = r * 0.06f, cap = StrokeCap.Round)
    drawPath(needle, Brush.linearGradient(listOf(needleColor.copy(alpha = 0.6f), needleColor, White), c, tip))
    if (style == GaugeStyle.CHROME) {
        drawCircle(Color(0xFF0A0B0C), r * 0.08f, c)
        drawCircle(Color.White.copy(alpha = 0.4f), r * 0.08f, c, style = Stroke(r * 0.012f))
    }
}

/** Half-dial over a road with the car seen from behind. */
private fun DrawScope.drawRoad(c: Offset, r: Float, f: Float, max: Float, color: Color, gearBox: Boolean) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF16181C), Face), c, r), r, c)
    chromeRing(c, r * 0.985f, r * 0.015f)
    val start = 160f
    val sweep = 220f
    // Lit ticks up to the value, dim after.
    val ticks = 66
    for (i in 0..ticks) {
        val deg = start + sweep * i / ticks
        val lit = i.toFloat() / ticks <= f
        val major = i % 6 == 0
        drawLine(
            if (lit) color else White.copy(alpha = if (major) 0.8f else 0.35f),
            polar(c, r * (if (major) 0.74f else 0.8f), deg), polar(c, r * 0.9f, deg),
            strokeWidth = if (major) r * 0.016f else r * 0.01f,
        )
    }
    glowArc(color, c, r * 0.93f, start, sweep * f.coerceAtLeast(0.002f), r * 0.02f)
    drawArc(color.copy(alpha = 0.3f), start, sweep, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(r * 0.008f))

    // Road in perspective
    val horizon = c.y + r * 0.42f
    val bottom = c.y + r * 0.92f
    val road = Path().apply {
        moveTo(c.x - r * 0.06f, horizon); lineTo(c.x + r * 0.06f, horizon)
        lineTo(c.x + r * 0.45f, bottom); lineTo(c.x - r * 0.45f, bottom); close()
    }
    clipPath(Path().apply { addOval(Rect(c, r * 0.97f)) }) {
        drawPath(road, Brush.verticalGradient(listOf(Color(0xFF0E0F11), Color(0xFF26292E)), horizon, bottom))
        drawLine(White.copy(alpha = 0.5f), Offset(c.x - r * 0.06f, horizon), Offset(c.x - r * 0.45f, bottom), strokeWidth = r * 0.008f)
        drawLine(White.copy(alpha = 0.5f), Offset(c.x + r * 0.06f, horizon), Offset(c.x + r * 0.45f, bottom), strokeWidth = r * 0.008f)
    }
    // Car from behind
    val cw = r * 0.2f
    val ch = r * 0.12f
    val cy = c.y + r * 0.6f
    drawRoundRect(Color(0xFF3A3E44), Offset(c.x - cw / 2, cy), Size(cw, ch), CornerRadius(r * 0.03f))
    drawRoundRect(Color(0xFF15171A), Offset(c.x - cw * 0.35f, cy - ch * 0.45f), Size(cw * 0.7f, ch * 0.55f), CornerRadius(r * 0.03f))
    drawRoundRect(Color(0xFFFF2D1F), Offset(c.x - cw * 0.46f, cy + ch * 0.3f), Size(cw * 0.22f, ch * 0.16f), CornerRadius(2f))
    drawRoundRect(Color(0xFFFF2D1F), Offset(c.x + cw * 0.24f, cy + ch * 0.3f), Size(cw * 0.22f, ch * 0.16f), CornerRadius(2f))
    drawRoundRect(Color.Black, Offset(c.x - cw * 0.48f, cy + ch * 0.9f), Size(cw * 0.18f, ch * 0.25f), CornerRadius(2f))
    drawRoundRect(Color.Black, Offset(c.x + cw * 0.3f, cy + ch * 0.9f), Size(cw * 0.18f, ch * 0.25f), CornerRadius(2f))

    if (gearBox) {
        val top = c.y + r * 0.78f
        val trap = Path().apply {
            moveTo(c.x - r * 0.14f, top); lineTo(c.x + r * 0.14f, top)
            lineTo(c.x + r * 0.18f, c.y + r * 0.94f); lineTo(c.x - r * 0.18f, c.y + r * 0.94f); close()
        }
        drawPath(trap, Color(0xFF0E0F11))
        drawPath(trap, color.copy(alpha = 0.6f), style = Stroke(r * 0.01f))
    }
}
