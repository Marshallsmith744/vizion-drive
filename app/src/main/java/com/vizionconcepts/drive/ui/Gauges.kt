package com.vizionconcepts.drive.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 240° arc gauge with tick marks. Value text in the middle.
 * [redFrom] colours the top of the scale (e.g. RPM red line).
 */
@Composable
fun ArcGauge(
    value: Float,
    min: Float,
    max: Float,
    valueText: String,
    unit: String,
    modifier: Modifier = Modifier,
    redFrom: Float? = null,
    majorTicks: Int = 8,
) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(((value - min) / (max - min)).coerceIn(0f, 1f), tween(250), label = "arc")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.07f
            val d = size.minDimension - stroke * 2
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            val start = 150f
            val sweep = 240f
            drawArc(p.dim.copy(alpha = 0.25f), start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            redFrom?.let {
                val f = ((it - min) / (max - min)).coerceIn(0f, 1f)
                drawArc(p.danger.copy(alpha = 0.45f), start + sweep * f, sweep * (1 - f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            val inRed = redFrom != null && value >= redFrom
            drawArc(
                Brush.sweepGradient(listOf(p.accent.copy(alpha = 0.5f), if (inRed) p.danger else p.accent)),
                start, sweep * anim, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // ticks
            val c = Offset(size.width / 2, size.height / 2)
            val rOuter = d / 2 - stroke * 1.3f
            for (i in 0..majorTicks * 2) {
                val ang = Math.toRadians((start + sweep * i / (majorTicks * 2)).toDouble())
                val len = if (i % 2 == 0) stroke * 0.9f else stroke * 0.45f
                val o = Offset(c.x + (rOuter * cos(ang)).toFloat(), c.y + (rOuter * sin(ang)).toFloat())
                val inner = Offset(c.x + ((rOuter - len) * cos(ang)).toFloat(), c.y + ((rOuter - len) * sin(ang)).toFloat())
                drawLine(p.dim.copy(alpha = 0.7f), inner, o, strokeWidth = stroke * 0.15f)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valueText, color = p.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = p.dim, fontSize = 13.sp)
        }
    }
}

/** Horizontal segmented bar (Tesla-style power/RPM bar) with optional shift light. */
@Composable
fun SegmentBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 30,
    redFrom: Float = 0.85f,
    shiftLight: Boolean = false,
) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(200), label = "bar")
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = (anim * segments).toInt()
        val flash = shiftLight && anim >= redFrom
        for (i in 0 until segments) {
            val f = (i + 1f) / segments
            val on = i < lit
            val col = when {
                !on -> p.dim.copy(alpha = 0.18f)
                flash -> p.danger
                f >= redFrom -> p.danger
                f >= redFrom - 0.15f -> p.warn
                else -> p.accent
            }
            drawRoundRect(col, Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
        }
    }
}

/** Thin progress line used by the vehicle panel and media widget. */
@Composable
fun ThinBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(p.dim.copy(alpha = 0.2f), cornerRadius = CornerRadius(3.dp.toPx()))
        drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(3.dp.toPx()))
    }
}

/** Compass rose that rotates with heading. */
@Composable
fun CompassRose(bearing: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(bearing, tween(400), label = "compass")
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.9f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(p.dim.copy(alpha = 0.3f), r, c, style = Stroke(2.dp.toPx()))
        for (i in 0 until 36) {
            val ang = Math.toRadians((i * 10 - anim - 90).toDouble())
            val len = if (i % 9 == 0) r * 0.18f else r * 0.07f
            drawLine(
                if (i == 0) p.danger else p.dim,
                Offset(c.x + ((r - len) * cos(ang)).toFloat(), c.y + ((r - len) * sin(ang)).toFloat()),
                Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat()),
                strokeWidth = if (i % 9 == 0) 3.dp.toPx() else 1.5f.dp.toPx(),
            )
        }
        // fixed heading pointer
        drawLine(p.accent, Offset(c.x, c.y - r * 0.55f), Offset(c.x, c.y - r * 1.02f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    }
}
