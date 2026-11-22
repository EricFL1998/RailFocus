package com.hsr.railfocus.ui.components.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.hsr.railfocus.ui.theme.LocalReduceMotion
import com.hsr.railfocus.ui.theme.Motion
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * 活地图效果层：昼夜色温 + 当前站呼吸环。
 * 叠加在 MapLibreView 上方，由 Compose 驱动，不占用地图线程。
 */
@Composable
fun LivingMapEffects(
    currentStationScreenPos: Offset?,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 昼夜色温层（每分钟更新一次，不是每帧）
        if (!reduceMotion) {
            DayNightTintOverlay()
        }

        // 2. 当前站呼吸环（12fps，只对当前站一个点）
        if (currentStationScreenPos != null && !reduceMotion) {
            StationBreathingRing(currentStationScreenPos)
        }
    }
}

/** 昼夜色温：随真实时间在 4 个关键帧间插值 */
@Composable
private fun DayNightTintOverlay() {
    val hour = produceState(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        while (true) {
            value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            kotlinx.coroutines.delay(60_000)
        }
    }.value
    val (tintColor, tintAlpha) = when (hour) {
        in 5..7 -> Color(0xFFFFB877) to 0.08f   // 清晨
        in 8..16 -> Color.White to 0f            // 正午
        in 17..19 -> Color(0xFFFF7A3D) to 0.12f  // 黄昏
        else -> Color(0xFF0A1B3A) to 0.18f       // 深夜
    }
    if (tintAlpha > 0f) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = tintColor.copy(alpha = tintAlpha))
        }
    }
}

/** 当前站呼吸环：缓慢扩散的圆环，12fps 足够 */
@Composable
private fun StationBreathingRing(center: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "station_breath")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breath",
    )
    val primary = com.hsr.railfocus.ui.theme.RailColors.Primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = 24f + progress * 20f  // 24 → 44dp
        val alpha = (1f - progress) * 0.35f
        drawCircle(
            color = primary.copy(alpha = alpha),
            radius = radius,
            center = center,
        )
    }
}
