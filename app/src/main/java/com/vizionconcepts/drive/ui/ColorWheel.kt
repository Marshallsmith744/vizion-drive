package com.vizionconcepts.drive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Full-brightness hue/saturation wheel. Tap or drag to pick; returns ARGB as Long. */
@Composable
fun ColorWheel(selected: Long, onPick: (Long) -> Unit, modifier: Modifier = Modifier.size(220.dp)) {
    val pick by rememberUpdatedState(onPick)
    var box by remember { mutableStateOf(IntSize.Zero) }
    fun choose(pos: Offset) {
        val r = min(box.width, box.height) / 2f
        if (r <= 0f) return
        val dx = pos.x - box.width / 2f
        val dy = pos.y - box.height / 2f
        val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
        val sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
        pick(Color.hsv(hue, sat, 1f).toArgb().toLong() and 0xFFFFFFFFL)
    }
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(selected.toInt(), it) }
    Canvas(
        modifier
            .onSizeChanged { box = it }
            .pointerInput(Unit) { detectTapGestures { choose(it) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> choose(change.position) } }
    ) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(
            Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), c),
            r, c,
        )
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), c, r), r, c)
        // Marker at the selected colour
        val a = Math.toRadians(hsv[0].toDouble())
        val m = Offset(c.x + (r * hsv[1] * cos(a)).toFloat(), c.y + (r * hsv[1] * sin(a)).toFloat())
        drawCircle(Color(selected), r * 0.09f, m)
        drawCircle(Color.Black, r * 0.09f, m, style = Stroke(r * 0.03f))
        drawCircle(Color.White, r * 0.11f, m, style = Stroke(r * 0.02f))
    }
}
