package com.vizionconcepts.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vizionconcepts.drive.settings.BackgroundStyle
import com.vizionconcepts.drive.settings.Settings
import com.vizionconcepts.drive.settings.ThemeMode
import kotlinx.coroutines.delay
import java.util.Calendar

@Immutable
data class DrivePalette(
    val dark: Boolean,
    val accent: Color,
    val text: Color,
    val dim: Color,
    val card: Color,
    val cardBorder: Color,
    val background: Brush,
    val warn: Color = Color(0xFFFF9F0A),
    val danger: Color = Color(0xFFFF453A),
    val ok: Color = Color(0xFF30D158),
)

val LocalPalette = staticCompositionLocalOf {
    DrivePalette(true, Color.Red, Color.White, Color.Gray, Color.DarkGray, Color.Gray, Brush.linearGradient(listOf(Color.Black, Color.Black)))
}

/** Night = 19:00–06:59 local time. */
fun isNight(hour: Int) = hour >= 19 || hour < 7

@Composable
fun DriveTheme(settings: Settings, content: @Composable () -> Unit) {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    }
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> isNight(hour)
    }
    val accent = Color(settings.accent)
    val palette = DrivePalette(
        dark = dark,
        accent = accent,
        text = if (dark) Color(0xFFF2F4F7) else Color(0xFF111418),
        dim = if (dark) Color(0xFF8A929C) else Color(0xFF5E6670),
        card = (if (dark) Color(0xFF15181D) else Color(0xFFFFFFFF)).copy(alpha = settings.cardOpacity),
        cardBorder = if (dark) Color(0x22FFFFFF) else Color(0x1A000000),
        background = backgroundBrush(settings.background, dark, accent),
    )
    val scheme = if (dark) darkColorScheme(primary = accent, secondary = accent)
    else lightColorScheme(primary = accent, secondary = accent)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPalette provides palette, content = content)
    }
}

fun backgroundBrush(style: BackgroundStyle, dark: Boolean, accent: Color): Brush {
    if (!dark) return Brush.verticalGradient(listOf(Color(0xFFEDEFF2), Color(0xFFD9DDE2)))
    return when (style) {
        BackgroundStyle.BLACK -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        BackgroundStyle.GRAPHITE -> Brush.verticalGradient(listOf(Color(0xFF1C1F24), Color(0xFF0A0B0D)))
        BackgroundStyle.MIDNIGHT -> Brush.verticalGradient(listOf(Color(0xFF0B1630), Color(0xFF03060D)))
        BackgroundStyle.CARBON -> Brush.linearGradient(
            colors = listOf(Color(0xFF16181B), Color(0xFF0D0E10), Color(0xFF16181B), Color(0xFF0D0E10)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(24f, 24f),
            tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
        )
        BackgroundStyle.IMAGE -> Brush.verticalGradient(listOf(Color(0xFF0A0B0D), Color.Black))
        BackgroundStyle.EMBER -> Brush.radialGradient(
            listOf(accent.copy(alpha = 0.35f), Color(0xFF0A0A0B)),
            radius = 1400f,
        )
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: Int = 14, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .padding(padding.dp),
        content = content,
    )
}
