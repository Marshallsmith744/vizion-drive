package com.vizionconcepts.drive.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vizionconcepts.drive.settings.GaugeStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * One gauge, many looks. Used for the vehicle panel, the speed widgets and the RPM widgets,
 * so changing the style in Settings → Gauges restyles everything at once.
 */
@Composable
fun StyledGauge(
    style: GaugeStyle,
    value: Float,
    max: Float,
    text: String,
    unit: String,
    modifier: Modifier = Modifier,
    redFrom: Float? = null,
    numberSize: TextUnit = 64.sp,
    numberColor: Color? = null,
    dialColor: Color? = null,
    gear: String? = null,
) {
    val p = LocalPalette.current
    if (style.isPremium()) {
        PremiumDial(style, value, max, text, unit, dialColor ?: p.accent, modifier, redFrom, gear)
        return
    }
    val inRed = redFrom != null && value >= redFrom
    val color = when {
        inRed -> p.danger
        numberColor != null -> numberColor
        else -> p.text
    }
    when (style) {
        GaugeStyle.DIGITAL -> Box(modifier, contentAlignment = Alignment.Center) {
            Number(text, unit, numberSize, color, FontWeight.Bold)
        }
        GaugeStyle.ARC -> ArcGauge(value, 0f, max, text, unit, modifier, redFrom)
        GaugeStyle.NEEDLE -> NeedleDial(value, max, text, unit, modifier, redFrom)
        GaugeStyle.RING -> RingGauge(value, max, text, unit, modifier, color, numberSize)
        GaugeStyle.BAR -> Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Number(text, unit, numberSize, color, FontWeight.Bold, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SegmentBar(value / max, redFrom = (redFrom ?: max * 0.9f) / max, shiftLight = redFrom != null)
        }
        GaugeStyle.HUD -> HudGauge(value, max, text, unit, modifier, color, numberSize)
        else -> Unit
    }
}

@Composable
private fun Number(text: String, unit: String, size: TextUnit, color: Color, weight: FontWeight, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = color, fontSize = size, fontWeight = weight, lineHeight = size)
        Text(unit.uppercase(), color = p.dim, fontSize = 14.sp, letterSpacing = 2.sp)
    }
}

/** Classic analogue dial: numbered scale and a sweeping needle. */
@Composable
private fun NeedleDial(value: Float, max: Float, text: String, unit: String, modifier: Modifier, redFrom: Float?) {
    val p = LocalPalette.current
    val anim by animateFloatAsState((value / max).coerceIn(0f, 1f), tween(250), label = "needle")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 * 0.92f
            val c = Offset(size.width / 2, size.height / 2)
            val start = 135f
            val sweep = 270f
            drawCircle(p.dim.copy(alpha = 0.10f), r, c)
            redFrom?.let {
                val f = (it / max).coerceIn(0f, 1f)
                drawArc(
                    p.danger.copy(alpha = 0.6f), start + sweep * f, sweep * (1 - f), false,
                    Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.06f),
                )
            }
            val major = if (max <= 10f) max.toInt().coerceAtLeast(1) else 10
            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = r * 0.11f
                color = android.graphics.Color.argb(
                    (p.dim.alpha * 255).toInt(), (p.dim.red * 255).toInt(), (p.dim.green * 255).toInt(), (p.dim.blue * 255).toInt(),
                )
            }
            for (i in 0..major * 5) {
                val ang = Math.toRadians((start + sweep * i / (major * 5)).toDouble())
                val isMajor = i % 5 == 0
                val len = if (isMajor) r * 0.12f else r * 0.05f
                drawLine(
                    if (isMajor) p.text else p.dim,
                    Offset(c.x + ((r - len) * cos(ang)).toFloat(), c.y + ((r - len) * sin(ang)).toFloat()),
                    Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat()),
                    strokeWidth = if (isMajor) r * 0.02f else r * 0.01f,
                )
                if (isMajor) {
                    val lr = r * 0.72f
                    val label = ((max / major) * (i / 5)).toInt().toString()
                    drawContext.canvas.nativeCanvas.drawText(
                        label, c.x + (lr * cos(ang)).toFloat(), c.y + (lr * sin(ang)).toFloat() + labelPaint.textSize / 3, labelPaint,
                    )
                }
            }
            val na = Math.toRadians((start + sweep * anim).toDouble())
            drawLine(
                p.accent, c,
                Offset(c.x + (r * 0.82f * cos(na)).toFloat(), c.y + (r * 0.82f * sin(na)).toFloat()),
                strokeWidth = r * 0.035f, cap = StrokeCap.Round,
            )
            drawCircle(p.accent, r * 0.07f, c)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = p.dim, fontSize = 12.sp)
        }
    }
}

/** Full 360° ring that fills with the value, number in the middle. */
@Composable
private fun RingGauge(value: Float, max: Float, text: String, unit: String, modifier: Modifier, color: Color, numberSize: TextUnit) {
    val p = LocalPalette.current
    val anim by animateFloatAsState((value / max).coerceIn(0f, 1f), tween(250), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.06f
            val d = size.minDimension - stroke * 2
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(p.dim.copy(alpha = 0.18f), -90f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
            // soft glow underneath
            drawArc(p.accent.copy(alpha = 0.25f), -90f, 360f * anim, false, tl, Size(d, d), style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(p.accent, -90f, 360f * anim, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Number(text, unit, (numberSize.value * 0.75f).sp, color, FontWeight.Bold)
    }
}

/** Minimal head-up style: thin oversized number over a fading accent scale. */
@Composable
private fun HudGauge(value: Float, max: Float, text: String, unit: String, modifier: Modifier, color: Color, numberSize: TextUnit) {
    val p = LocalPalette.current
    val anim by animateFloatAsState((value / max).coerceIn(0f, 1f), tween(250), label = "hud")
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = color, fontSize = (numberSize.value * 1.15f).sp, fontWeight = FontWeight.Thin, lineHeight = (numberSize.value * 1.15f).sp)
        Canvas(Modifier.fillMaxWidth(0.85f).height(14.dp)) {
            val ticks = 40
            for (i in 0..ticks) {
                val x = size.width * i / ticks
                val h = if (i % 5 == 0) size.height else size.height * 0.5f
                val lit = i.toFloat() / ticks <= anim
                drawLine(
                    if (lit) p.accent else p.dim.copy(alpha = 0.3f),
                    Offset(x, size.height - h), Offset(x, size.height), strokeWidth = 2.dp.toPx(),
                )
            }
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, p.accent, Color.Transparent)),
                Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx(),
            )
        }
        Text(unit.uppercase(), color = p.dim, fontSize = 13.sp, letterSpacing = 4.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
