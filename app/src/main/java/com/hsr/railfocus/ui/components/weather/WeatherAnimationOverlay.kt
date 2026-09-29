package com.hsr.railfocus.ui.components.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hsr.railfocus.domain.model.WeatherCondition
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 全局电影级动态天气与大气渲染系统。
 */
@Composable
fun WeatherAnimationOverlay(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    isNight: Boolean = com.hsr.railfocus.domain.model.isNightNow(),
) {
    val isVisible = condition != null && condition != WeatherCondition.UNKNOWN

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(450)),
        exit = fadeOut(tween(240)),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (condition) {
                WeatherCondition.CLEAR -> {
                    if (isNight) {
                        CinematicMoonlightCanvas()
                    } else {
                        CinematicSunbeamCanvas()
                    }
                }

                WeatherCondition.CLOUDY -> {
                    CinematicCloudyCanvas(isNight = isNight)
                }

                WeatherCondition.OVERCAST -> {
                    CinematicOvercastAtmosphereCanvas()
                }

                WeatherCondition.LIGHT_RAIN -> {
                    // 小雨：细雨连绵轻柔，雨线偏细短，轻风下落，清晰可见但温和
                    CinematicRainCanvas(
                        densityMultiplier = 0.75f,
                        speedMultiplier = 0.88f,
                        lengthMultiplier = 0.80f,
                        strokeMultiplier = 0.90f,
                        splashRate = 0.12f,
                        atmosphereAlpha = 0.10f,
                        windBaseSlope = -0.10f,
                        windVariation = 0.025f,
                    )
                }

                WeatherCondition.MODERATE_RAIN -> {
                    // 中雨：标准连绵密集雨势，斜织雨幕，明显地表涟漪水花
                    CinematicRainCanvas(
                        densityMultiplier = 1.45f,
                        speedMultiplier = 1.15f,
                        lengthMultiplier = 1.05f,
                        strokeMultiplier = 1.0f,
                        splashRate = 0.32f,
                        atmosphereAlpha = 0.18f,
                        windBaseSlope = -0.16f,
                        windVariation = 0.04f,
                    )
                }

                WeatherCondition.HEAVY_RAIN -> {
                    // 大雨：倾盆急促密集雨帘，厚重降雨，较强水花，天幕明显加深
                    CinematicRainCanvas(
                        densityMultiplier = 2.10f,
                        speedMultiplier = 1.35f,
                        lengthMultiplier = 1.35f,
                        strokeMultiplier = 1.20f,
                        splashRate = 0.50f,
                        atmosphereAlpha = 0.26f,
                        windBaseSlope = -0.20f,
                        windVariation = 0.06f,
                    )
                }

                WeatherCondition.STORM_RAIN -> {
                    // 暴雨：密织狂暴长雨帘，极速俯冲，大风偏斜，低气压沉暗天幕
                    CinematicRainCanvas(
                        densityMultiplier = 2.75f,
                        speedMultiplier = 1.55f,
                        lengthMultiplier = 1.65f,
                        strokeMultiplier = 1.35f,
                        splashRate = 0.65f,
                        atmosphereAlpha = 0.36f,
                        windBaseSlope = -0.25f,
                        windVariation = 0.08f,
                    )
                }

                WeatherCondition.THUNDER_SHOWER -> {
                    // 雷阵雨：大暴雨雨势 + 偶发天际双闪电光
                    CinematicRainCanvas(
                        densityMultiplier = 2.35f,
                        speedMultiplier = 1.45f,
                        lengthMultiplier = 1.50f,
                        strokeMultiplier = 1.30f,
                        splashRate = 0.55f,
                        atmosphereAlpha = 0.30f,
                        windBaseSlope = -0.22f,
                        windVariation = 0.07f,
                        enableLightning = true,
                    )
                }

                WeatherCondition.LIGHT_SNOW -> {
                    // 小雪：轻柔微晶粉雪，细细漫舞
                    CinematicSnowCanvas(
                        flakeMultiplier = 0.70f,
                        speedMultiplier = 0.85f,
                    )
                }

                WeatherCondition.MODERATE_SNOW -> {
                    // 中雪：细密雪花，轻盈连绵
                    CinematicSnowCanvas(
                        flakeMultiplier = 1.0f,
                        speedMultiplier = 1.0f,
                    )
                }

                WeatherCondition.HEAVY_SNOW -> {
                    // 大雪：密集细雪倾洒，天地银装
                    CinematicSnowCanvas(
                        flakeMultiplier = 1.45f,
                        speedMultiplier = 1.25f,
                    )
                }

                WeatherCondition.STORM_SNOW -> {
                    // 暴雪：疾风骤雪，风雪漫卷
                    CinematicSnowCanvas(
                        flakeMultiplier = 1.85f,
                        speedMultiplier = 1.45f,
                    )
                }

                WeatherCondition.SLEET -> {
                    CinematicRainCanvas(densityMultiplier = 0.7f, speedMultiplier = 0.95f)
                    CinematicSnowCanvas(flakeMultiplier = 0.6f, speedMultiplier = 0.85f)
                }

                WeatherCondition.LIGHT_FOG -> {
                    // 轻雾：淡雅空灵山岚薄雾，通透轻盈
                    CinematicMistCanvas(
                        ribbonAlpha = 0.22f,
                        ambientAlpha = 0.06f,
                        tintColor = Color(0xFFECEFF1),
                    )
                }

                WeatherCondition.MODERATE_FOG -> {
                    // 大雾：平流山野雾带，沉静飘逸
                    CinematicMistCanvas(
                        ribbonAlpha = 0.40f,
                        ambientAlpha = 0.14f,
                        tintColor = Color(0xFFECEFF1),
                    )
                }

                WeatherCondition.HEAVY_FOG -> {
                    // 浓雾：厚重山原平流雾海，苍茫沉凝
                    CinematicMistCanvas(
                        ribbonAlpha = 0.58f,
                        ambientAlpha = 0.22f,
                        tintColor = Color(0xFFCFD8DC),
                    )
                }

                WeatherCondition.LIGHT_HAZE -> {
                    // 轻度霾：微干燥浮空薄霭
                    CinematicMistCanvas(
                        ribbonAlpha = 0.22f,
                        ambientAlpha = 0.08f,
                        tintColor = Color(0xFFEFEBE9),
                    )
                }

                WeatherCondition.MODERATE_HAZE -> {
                    // 中度霾：温润灰黄烟霭漫游
                    CinematicMistCanvas(
                        ribbonAlpha = 0.38f,
                        ambientAlpha = 0.16f,
                        tintColor = Color(0xFFD7CCC8),
                    )
                }

                WeatherCondition.HEAVY_HAZE -> {
                    // 重度霾：厚重浮尘低气压沉暮
                    CinematicMistCanvas(
                        ribbonAlpha = 0.55f,
                        ambientAlpha = 0.25f,
                        tintColor = Color(0xFFBCAAA4),
                    )
                }

                WeatherCondition.DUST -> {
                    CinematicSandstormCanvas(isDust = true)
                }

                WeatherCondition.SAND -> {
                    CinematicSandstormCanvas(isDust = false)
                }

                else -> Unit
            }
        }
    }
}

/* ========================================================================= */
/*                     1. 电影级雨天与雷阵雨粒子渲染系统                     */
/* ========================================================================= */

private class RainDrop(
    var x: Float,
    var y: Float,
    var length: Float,
    var speed: Float,
    var strokeWidth: Float,
    var alpha: Float,
    var layer: Int,
    var wobbleSpeed: Float = 0f,
    var wobbleRadius: Float = 0f,
    var wobblePhase: Float = 0f,
)

private class RainSplash(
    var x: Float,
    var y: Float,
    var radius: Float,
    var maxRadius: Float,
    var alpha: Float,
    var active: Boolean = false,
)

@Composable
private fun CinematicRainCanvas(
    densityMultiplier: Float = 1.0f,
    speedMultiplier: Float = 1.0f,
    lengthMultiplier: Float = 1.0f,
    strokeMultiplier: Float = 1.0f,
    splashRate: Float = 0.25f,
    atmosphereAlpha: Float = 0.16f,
    windBaseSlope: Float = -0.16f,
    windVariation: Float = 0.04f,
    enableLightning: Boolean = false,
    enableAtmosphere: Boolean = true,
) {
    val density = LocalDensity.current
    val bgCount = (45 * densityMultiplier).toInt()
    val midCount = (35 * densityMultiplier).toInt()
    val fgCount = (12 * densityMultiplier).toInt()
    val totalDrops = bgCount + midCount + fgCount

    val drops = remember(densityMultiplier) {
        val rnd = Random(System.currentTimeMillis())
        val list = ArrayList<RainDrop>(totalDrops)
        var dropIndex = 0

        fun createDrop(layer: Int, lengthRange: ClosedFloatingPointRange<Float>, speedRange: ClosedFloatingPointRange<Float>, strokeWidth: Float, alphaBase: Float, alphaSpread: Float): RainDrop {
            // 分层离散化打散 X 坐标，加入充足抖动，杜绝雨滴集中在同一直线上
            val stratX = (dropIndex.toFloat() / totalDrops.coerceAtLeast(1)) * 2000f - 200f
            val jitterX = (rnd.nextFloat() - 0.5f) * 160f
            dropIndex++

            return RainDrop(
                x = stratX + jitterX,
                y = rnd.nextFloat() * 3200f - 400f,
                length = (rnd.nextFloat() * (lengthRange.endInclusive - lengthRange.start) + lengthRange.start) * lengthMultiplier * density.density,
                speed = (rnd.nextFloat() * (speedRange.endInclusive - speedRange.start) + speedRange.start) * speedMultiplier * density.density,
                strokeWidth = strokeWidth * strokeMultiplier * density.density,
                alpha = rnd.nextFloat() * alphaSpread + alphaBase,
                layer = layer,
                wobbleSpeed = rnd.nextFloat() * 2.2f + 1.2f,
                wobbleRadius = (rnd.nextFloat() * 3.5f + 1.5f) * density.density,
                wobblePhase = rnd.nextFloat() * 6.28f,
            )
        }

        repeat(bgCount) {
            list.add(createDrop(0, 12f..24f, 700f..1400f, 0.75f, 0.25f, 0.18f))
        }
        repeat(midCount) {
            list.add(createDrop(1, 18f..38f, 800f..1900f, 1.15f, 0.42f, 0.25f))
        }
        repeat(fgCount) {
            list.add(createDrop(2, 26f..54f, 900f..2500f, 1.65f, 0.60f, 0.25f))
        }
        list.shuffle(rnd)
        list
    }

    val splashes = remember {
        List(14) { RainSplash(0f, 0f, 0f, 16f * density.density, 0f, false) }
    }

    var lightningAlpha by remember { mutableFloatStateOf(0f) }
    var nextLightningNanos by remember { mutableLongStateOf(0L) }
    var frameTicker by remember { mutableLongStateOf(0L) }
    var globalTimeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        nextLightningNanos = System.nanoTime() + Random.nextLong(7_000_000_000L, 14_000_000_000L)

        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val deltaSeconds = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos
                globalTimeSeconds += deltaSeconds

                val windSlope = windBaseSlope + sin(globalTimeSeconds * 0.4f) * windVariation

                for (d in drops) {
                    d.y += d.speed * deltaSeconds
                    d.x += (d.speed * windSlope) * deltaSeconds
                }

                for (s in splashes) {
                    if (s.active) {
                        s.radius += 40f * density.density * deltaSeconds
                        s.alpha -= 2.2f * deltaSeconds
                        if (s.alpha <= 0f || s.radius >= s.maxRadius) {
                            s.active = false
                        }
                    }
                }

                if (enableLightning) {
                    if (nowNanos > nextLightningNanos) {
                        val elapsed = (nowNanos - nextLightningNanos) / 1_000_000L
                        lightningAlpha = when {
                            elapsed < 50 -> 0.20f
                            elapsed < 90 -> 0.05f
                            elapsed < 170 -> 0.38f
                            elapsed < 280 -> 0.18f * (1f - (elapsed - 170) / 110f)
                            else -> {
                                nextLightningNanos = nowNanos + Random.nextLong(8_000_000_000L, 16_000_000_000L)
                                0f
                            }
                        }
                    } else {
                        lightningAlpha = 0f
                    }
                }

                frameTicker = nowNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        if (enableAtmosphere && atmosphereAlpha > 0.01f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF263238).copy(alpha = atmosphereAlpha),
                        Color(0xFF37474F).copy(alpha = atmosphereAlpha * 0.5f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = h * 0.50f,
                ),
                size = size,
            )
        }

        if (lightningAlpha > 0.01f) {
            val lAlpha = lightningAlpha.coerceIn(0f, 0.4f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE1F5FE).copy(alpha = lAlpha),
                        Color(0xFFB3E5FC).copy(alpha = lAlpha * 0.6f),
                        Color.White.copy(alpha = lAlpha * 0.2f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.8f, h * 0.05f),
                    radius = w * 1.2f,
                ),
                size = size,
            )
        }

        val splashStroke = Stroke(width = 1.4f * density.density)
        for (s in splashes) {
            if (s.active && s.alpha > 0.01f) {
                // 底层深水蓝微涟漪（在浅色底图提供清晰轮廓）
                drawOval(
                    color = Color(0xFF0277BD).copy(alpha = s.alpha * 0.45f),
                    topLeft = Offset(s.x - s.radius, s.y - s.radius * 0.35f),
                    size = Size(s.radius * 2f, s.radius * 0.7f),
                    style = Stroke(width = 1.8f * density.density),
                )
                // 顶层高亮水花
                drawOval(
                    color = Color(0xFFE1F5FE).copy(alpha = s.alpha.coerceIn(0f, 0.70f)),
                    topLeft = Offset(s.x - s.radius, s.y - s.radius * 0.35f),
                    size = Size(s.radius * 2f, s.radius * 0.7f),
                    style = splashStroke,
                )
            }
        }

        val windSlope = windBaseSlope + sin(globalTimeSeconds * 0.4f) * windVariation
        val rainHeadColor = Color(0xFFE1F5FE)
        val rainTailColor = Color(0xFF81D4FA)

        val windDriftSpan = (h * kotlin.math.abs(windSlope)).coerceAtLeast(200f)

        for (d in drops) {
            if (d.y > h + d.length) {
                if (d.layer >= 1 && Random.nextFloat() < splashRate) {
                    val freeSplash = splashes.firstOrNull { !it.active }
                    if (freeSplash != null) {
                        freeSplash.x = d.x
                        freeSplash.y = h - Random.nextFloat() * 18.dp.toPx()
                        freeSplash.radius = 2f
                        freeSplash.maxRadius = (Random.nextFloat() * 10f + 10f) * density.density
                        freeSplash.alpha = 0.45f
                        freeSplash.active = true
                    }
                }
                // 纵向深度错落重置（覆盖 -d.length 到上方 400dp），完全杜绝雨滴结队成排下落
                d.y = -d.length - Random.nextFloat() * (h * 0.35f + 120f)
                // 横向随机覆盖全风道缓冲区，打散初始位置
                d.x = Random.nextFloat() * (w + windDriftSpan * 1.5f) - windDriftSpan * 0.35f
            }

            // 飘出左右边界时，彻底随机重置坐标，避免固定在某个单一 X 点集中出现
            if (d.x < -windDriftSpan * 0.5f - 80f) {
                d.x = w + Random.nextFloat() * (windDriftSpan + 100f)
                d.y = Random.nextFloat() * (h * 0.7f) - d.length
            } else if (d.x > w + windDriftSpan + 120f) {
                d.x = -Random.nextFloat() * 100f
                d.y = Random.nextFloat() * (h * 0.7f) - d.length
            }

            // 引入微气流湍流横向微漂移，彻底打破生硬的直线排列视错觉
            val wobbleX = sin(globalTimeSeconds * d.wobbleSpeed + d.wobblePhase) * d.wobbleRadius
            val currentDropX = d.x + wobbleX

            val slantX = d.length * windSlope
            val startOffset = Offset(currentDropX - slantX, d.y - d.length)
            val endOffset = Offset(currentDropX, d.y)

            // 1. 底层深水蓝/暗影雨线（确保在白色与米黄浅色底图上极其清晰鲜明）
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF37474F).copy(alpha = d.alpha * 0.45f),
                        Color(0xFF0277BD).copy(alpha = d.alpha * 0.75f),
                    ),
                    start = startOffset,
                    end = endOffset,
                ),
                start = startOffset,
                end = endOffset,
                strokeWidth = d.strokeWidth * 1.15f,
                cap = StrokeCap.Round,
            )

            // 2. 顶层水光雨丝高亮层（在深色按钮/卡片上保持晶莹剔透的水光反光）
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF4FC3F7).copy(alpha = d.alpha * 0.50f),
                        Color(0xFFE1F5FE).copy(alpha = d.alpha * 0.95f),
                    ),
                    start = startOffset,
                    end = endOffset,
                ),
                start = startOffset,
                end = endOffset,
                strokeWidth = d.strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private class SnowFlake(
    var initialX: Float,
    var y: Float,
    var radius: Float,
    var speed: Float,
    var alpha: Float,
    var swaySpeed1: Float,
    var swayRadius1: Float,
    var swayPhase1: Float,
    var swaySpeed2: Float,
    var swayRadius2: Float,
    var swayPhase2: Float,
)

@Composable
private fun CinematicSnowCanvas(
    flakeMultiplier: Float = 1.0f,
    speedMultiplier: Float = 1.0f,
) {
    val density = LocalDensity.current
    val bgCount = (72 * flakeMultiplier).toInt()
    val midCount = (52 * flakeMultiplier).toInt()
    val fgCount = (18 * flakeMultiplier).toInt()
    val totalFlakes = bgCount + midCount + fgCount

    val flakes = remember(flakeMultiplier) {
        val rnd = Random(202)
        val list = ArrayList<SnowFlake>(totalFlakes)
        // 远景微晶细雪（细如微尘粉雪，0.7 ~ 1.2dp）
        repeat(bgCount) {
            list.add(
                SnowFlake(
                    initialX = rnd.nextFloat() * 1500f,
                    y = rnd.nextFloat() * 2800f,
                    radius = (rnd.nextFloat() * 0.5f + 0.7f) * density.density,
                    speed = (rnd.nextFloat() * 30f + 40f) * speedMultiplier * density.density,
                    alpha = rnd.nextFloat() * 0.30f + 0.45f,
                    swaySpeed1 = rnd.nextFloat() * 0.9f + 0.6f,
                    swayRadius1 = (rnd.nextFloat() * 9f + 5f) * density.density,
                    swayPhase1 = rnd.nextFloat() * 6.28f,
                    swaySpeed2 = rnd.nextFloat() * 1.6f + 1.2f,
                    swayRadius2 = (rnd.nextFloat() * 5f + 3f) * density.density,
                    swayPhase2 = rnd.nextFloat() * 6.28f,
                )
            )
        }
        // 中景真实细雪花（1.2 ~ 1.9dp，精致晶莹）
        repeat(midCount) {
            list.add(
                SnowFlake(
                    initialX = rnd.nextFloat() * 1500f,
                    y = rnd.nextFloat() * 2800f,
                    radius = (rnd.nextFloat() * 0.7f + 1.2f) * density.density,
                    speed = (rnd.nextFloat() * 40f + 60f) * speedMultiplier * density.density,
                    alpha = rnd.nextFloat() * 0.35f + 0.55f,
                    swaySpeed1 = rnd.nextFloat() * 1.1f + 0.8f,
                    swayRadius1 = (rnd.nextFloat() * 14f + 8f) * density.density,
                    swayPhase1 = rnd.nextFloat() * 6.28f,
                    swaySpeed2 = rnd.nextFloat() * 2.0f + 1.5f,
                    swayRadius2 = (rnd.nextFloat() * 7f + 4f) * density.density,
                    swayPhase2 = rnd.nextFloat() * 6.28f,
                )
            )
        }
        // 近景微掠轻柔细雪片（1.9 ~ 2.6dp，最大不超过 2.6dp，绝无任何大圆盘或大泡泡）
        repeat(fgCount) {
            list.add(
                SnowFlake(
                    initialX = rnd.nextFloat() * 1500f,
                    y = rnd.nextFloat() * 2800f,
                    radius = (rnd.nextFloat() * 0.7f + 1.9f) * density.density,
                    speed = (rnd.nextFloat() * 50f + 80f) * speedMultiplier * density.density,
                    alpha = rnd.nextFloat() * 0.30f + 0.65f,
                    swaySpeed1 = rnd.nextFloat() * 1.2f + 0.9f,
                    swayRadius1 = (rnd.nextFloat() * 16f + 10f) * density.density,
                    swayPhase1 = rnd.nextFloat() * 6.28f,
                    swaySpeed2 = rnd.nextFloat() * 2.4f + 1.8f,
                    swayRadius2 = (rnd.nextFloat() * 8f + 4f) * density.density,
                    swayPhase2 = rnd.nextFloat() * 6.28f,
                )
            )
        }
        list
    }

    var frameTicker by remember { mutableLongStateOf(0L) }
    var totalElapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val deltaSeconds = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos
                totalElapsedSeconds += deltaSeconds

                for (f in flakes) {
                    f.y += f.speed * deltaSeconds
                }
                frameTicker = nowNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x18E0F2F1),
                    Color(0x0AE0F7FA),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = h * 0.35f,
            ),
            size = size,
        )

        val coreWhite = Color.White
        val shadowRim = Color(0xFF455A64)
        val rimBlue = Color(0xFF81D4FA)

        for (f in flakes) {
            if (f.y > h + f.radius * 2) {
                f.y = -f.radius * 2 - Random.nextFloat() * 40f
                f.initialX = Random.nextFloat() * (w + 120f)
            }

            val currentX = f.initialX +
                sin(totalElapsedSeconds * f.swaySpeed1 + f.swayPhase1) * f.swayRadius1 +
                cos(totalElapsedSeconds * f.swaySpeed2 + f.swayPhase2) * f.swayRadius2

            val center = Offset(currentX, f.y)

            // 1. 极微弱冷灰反差底晕（比晶核仅多 0.45dp 极窄外缘，白色地图与米黄浅色底图上清晰可辨，绝不发胀）
            drawCircle(
                color = shadowRim.copy(alpha = f.alpha * 0.35f),
                radius = f.radius + 0.45f * density.density,
                center = center,
            )
            // 2. 微弱晶莹反光漫射层
            drawCircle(
                color = rimBlue.copy(alpha = f.alpha * 0.25f),
                radius = f.radius * 1.12f,
                center = center,
            )
            // 3. 纯白细雪晶核（真实自然的微型细雪点）
            drawCircle(
                color = coreWhite.copy(alpha = f.alpha),
                radius = f.radius,
                center = center,
            )
        }
    }
}

/* ========================================================================= */
/*                     3. 晴日天光与日晕耀斑光学系统 (☀️ CLEAR)               */
/* ========================================================================= */

@Composable
private fun CinematicSunbeamCanvas() {
    var frameTicker by remember { mutableLongStateOf(0L) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val delta = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos
                timeSeconds += delta
                frameTicker = nowNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val sunOrigin = Offset(w * 0.88f, h * 0.06f)
        val breath = (sin(timeSeconds * 0.7f) + 1f) * 0.5f
        val screenCenter = Offset(w * 0.5f, h * 0.5f)

        // 1. 太阳核心耀斑与天际金辉
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f + breath * 0.10f),
                    Color(0xFFFFF9C4).copy(alpha = 0.30f + breath * 0.08f),
                    Color(0xFFFFE082).copy(alpha = 0.16f + breath * 0.05f),
                    Color(0xFFFFCA28).copy(alpha = 0.05f + breath * 0.03f),
                    Color.Transparent,
                ),
                center = sunOrigin,
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = sunOrigin,
        )

        // 2. 拟真同心日晕环
        val haloRadius1 = w * 0.38f + breath * 12f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFECB3).copy(alpha = 0.08f + breath * 0.04f),
                    Color(0xFFE1F5FE).copy(alpha = 0.05f + breath * 0.03f),
                    Color(0xFFFFE082).copy(alpha = 0.07f + breath * 0.03f),
                    Color.Transparent,
                ),
                center = sunOrigin,
                radius = haloRadius1 + 18.dp.toPx(),
            ),
            radius = haloRadius1 + 18.dp.toPx(),
            center = sunOrigin,
            style = Stroke(width = 16.dp.toPx()),
        )

        val haloRadius2 = w * 0.72f + breath * 20f
        drawCircle(
            color = Color(0xFFFFE082).copy(alpha = 0.04f + breath * 0.02f),
            radius = haloRadius2,
            center = sunOrigin,
            style = Stroke(width = 24.dp.toPx()),
        )

        // 3. 沿光学轴线的镜头耀斑散焦光晕
        val flareVector = screenCenter - sunOrigin

        val flarePos1 = sunOrigin + flareVector * (0.42f + breath * 0.02f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF176).copy(alpha = 0.12f),
                    Color(0xFF80DEEA).copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = flarePos1,
                radius = 32.dp.toPx(),
            ),
            radius = 32.dp.toPx(),
            center = flarePos1,
        )

        val flarePos2 = sunOrigin + flareVector * (0.78f - breath * 0.02f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFE082).copy(alpha = 0.07f + breath * 0.03f),
                    Color(0xFFB2EBF2).copy(alpha = 0.03f),
                    Color.Transparent,
                ),
                center = flarePos2,
                radius = 70.dp.toPx(),
            ),
            radius = 70.dp.toPx(),
            center = flarePos2,
        )

        // 4. 丁达尔神圣光束
        val rayAngles = listOf(-0.48f, -0.68f, -0.90f)
        val rayWidths = listOf(w * 0.32f, w * 0.25f, w * 0.35f)

        for (i in rayAngles.indices) {
            val baseAngle = rayAngles[i] + sin(timeSeconds * 0.3f + i * 1.5f) * 0.04f
            val rayWidth = rayWidths[i]
            val rayAlpha = (0.05f + breath * 0.03f + sin(timeSeconds * 0.6f + i * 2f) * 0.015f).coerceIn(0.02f, 0.10f)

            val path = Path().apply {
                moveTo(sunOrigin.x, sunOrigin.y)
                lineTo(sunOrigin.x + cos(baseAngle) * h * 1.6f - rayWidth / 2, sunOrigin.y - sin(baseAngle) * h * 1.6f)
                lineTo(sunOrigin.x + cos(baseAngle) * h * 1.6f + rayWidth / 2, sunOrigin.y - sin(baseAngle) * h * 1.6f)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFE082).copy(alpha = rayAlpha * 1.5f),
                        Color(0xFFFFD54F).copy(alpha = rayAlpha),
                        Color.Transparent,
                    ),
                    start = sunOrigin,
                    end = Offset(0f, h * 0.85f),
                ),
            )
        }
    }
}

/* ========================================================================= */
/*                     3.1 晴夜明月与静谧月晕系统 (🌙 CLEAR NIGHT)            */
/* ========================================================================= */

/**
 * 晴朗夜空：清冷明月、静谧月晕光华与微弱繁星系统。
 * 在夜晚或深色模式下自动生效，呈现柔美皎洁的夜空月光，绝无突兀强烈的日间太阳强光。
 */
@Composable
private fun CinematicMoonlightCanvas() {
    var frameTicker by remember { mutableLongStateOf(0L) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val delta = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos
                timeSeconds += delta
                frameTicker = nowNanos
            }
        }
    }

    val density = LocalDensity.current
    val stars = remember {
        val rnd = Random(707)
        List(24) {
            Offset(rnd.nextFloat(), rnd.nextFloat()) to (rnd.nextFloat() * 2.2f + 1.2f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val moonOrigin = Offset(w * 0.86f, h * 0.08f)
        val breath = (sin(timeSeconds * 0.45f) + 1f) * 0.5f

        // 1. 夜空静谧微弱繁星（疏落有致，轻柔微闪）
        for (i in stars.indices) {
            val (ratio, blinkSpeed) = stars[i]
            val sx = ratio.x * w
            val sy = ratio.y * (h * 0.65f)
            val distToMoon = (Offset(sx, sy) - moonOrigin).getDistance()
            if (distToMoon > w * 0.28f) {
                val starAlpha = (0.18f + sin(timeSeconds * blinkSpeed + i * 1.3f) * 0.35f).coerceIn(0f, 0.60f)
                drawCircle(
                    color = Color(0xFFE0F7FA).copy(alpha = starAlpha),
                    radius = (0.75f + (i % 3) * 0.35f) * density.density,
                    center = Offset(sx, sy),
                )
            }
        }

        // 2. 月轮核心清辉与静谧月光水色（银蓝与皓白冷调）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f + breath * 0.08f),
                    Color(0xFFE0F7FA).copy(alpha = 0.30f + breath * 0.06f),
                    Color(0xFF80DEEA).copy(alpha = 0.12f + breath * 0.03f),
                    Color(0xFF283593).copy(alpha = 0.03f),
                    Color.Transparent,
                ),
                center = moonOrigin,
                radius = w * 0.52f,
            ),
            radius = w * 0.52f,
            center = moonOrigin,
        )

        // 3. 静谧银辉月晕环（22° 柔和月晕光圈）
        val moonHaloRadius = w * 0.34f + breath * 10f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFE0F7FA).copy(alpha = 0.07f + breath * 0.03f),
                    Color(0xFF80DEEA).copy(alpha = 0.04f + breath * 0.02f),
                    Color(0xFFB2EBF2).copy(alpha = 0.06f + breath * 0.02f),
                    Color.Transparent,
                ),
                center = moonOrigin,
                radius = moonHaloRadius + 14.dp.toPx(),
            ),
            radius = moonHaloRadius + 14.dp.toPx(),
            center = moonOrigin,
            style = Stroke(width = 12.dp.toPx()),
        )

        // 4. 清冷月光光学散焦光斑（轴线银青色冷调反光）
        val screenCenter = Offset(w * 0.5f, h * 0.5f)
        val flareVector = screenCenter - moonOrigin
        val moonFlare1 = moonOrigin + flareVector * (0.38f + breath * 0.02f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF80DEEA).copy(alpha = 0.09f),
                    Color(0xFFE0F7FA).copy(alpha = 0.04f),
                    Color.Transparent,
                ),
                center = moonFlare1,
                radius = 28.dp.toPx(),
            ),
            radius = 28.dp.toPx(),
            center = moonFlare1,
        )
    }
}

/* ========================================================================= */
/*                     4. 多云：全景漫游纯白积云与破云天光系统 (⛅ CLOUDY)    */
/* ========================================================================= */

private data class CloudPuffDef(
    val dx: Float,
    val dy: Float,
    val radiusFraction: Float,
    val scaleX: Float,
    val scaleY: Float,
    val alphaFactor: Float,
)

/**
 * 绘制自然透光、轮廓清晰白皙的飘浮积云团。
 * 彻底杜绝任何阴影灰与底层脏灰色值，采用纯白与日光金辉多重高斯羽化交叠，
 * 呈现清晰分明的云朵造型，同时保持底图道路与卡片清晰可读。
 */
private fun DrawScope.drawSoftCloudCluster(
    centerX: Float,
    centerY: Float,
    baseWidth: Float,
    baseHeight: Float,
    alpha: Float,
    time: Float,
    morphOffset: Float = 0f,
    isNight: Boolean = false,
) {
    if (alpha <= 0.005f) return

    val breathe = sin(time * 0.45f + morphOffset) * 0.08f
    val puffWidth = baseWidth * (1f + breathe)
    val puffHeight = baseHeight * (1f - breathe * 0.4f)

    val puffs = listOf(
        // 主核中心饱满云团
        CloudPuffDef(0.0f, 0.0f, 0.44f, 2.7f, 0.95f, 1.0f),
        // 顶部翻滚云冠（承接暖阳微光，明朗白皙）
        CloudPuffDef(-0.18f, -0.22f, 0.36f, 2.3f, 1.10f, 0.95f),
        CloudPuffDef(0.16f, -0.20f, 0.38f, 2.4f, 1.05f, 0.98f),
        CloudPuffDef(0.02f, -0.30f, 0.30f, 2.2f, 1.00f, 0.92f),
        // 侧翼自然延展云羽
        CloudPuffDef(-0.36f, 0.02f, 0.32f, 2.8f, 0.85f, 0.82f),
        CloudPuffDef(0.38f, 0.04f, 0.34f, 2.9f, 0.80f, 0.80f),
        // 底部平缓凝结基底
        CloudPuffDef(-0.10f, 0.16f, 0.36f, 3.1f, 0.75f, 0.88f),
        CloudPuffDef(0.14f, 0.14f, 0.34f, 3.0f, 0.75f, 0.85f),
    )

    for (p in puffs) {
        val px = centerX + p.dx * puffWidth
        val py = centerY + p.dy * puffHeight
        val radius = puffHeight * p.radiusFraction
        val currentAlpha = (alpha * p.alphaFactor).coerceIn(0f, 1f)

        withTransform({
            scale(scaleX = p.scaleX, scaleY = p.scaleY, pivot = Offset(px, py))
        }) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = currentAlpha),
                    0.38f to Color(0xFFFFFDF8).copy(alpha = currentAlpha * 0.90f),
                    0.68f to Color(0xFFF9FBFB).copy(alpha = currentAlpha * 0.55f),
                    0.86f to Color.White.copy(alpha = currentAlpha * 0.20f),
                    1.0f to Color.Transparent,
                    center = Offset(px, py),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(px, py),
            )
        }
    }
}

@Composable
private fun CinematicCloudyCanvas(isNight: Boolean = false) {
    var frameTicker by remember { mutableLongStateOf(0L) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val delta = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos
                timeSeconds += delta
                frameTicker = nowNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val density = density

        // 1. 右上天际破云暖阳与天幕日晕
        val sunCenter = Offset(w * 0.88f, h * 0.07f)
        val breath = (sin(timeSeconds * 0.5f) + 1f) * 0.5f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF9C4).copy(alpha = 0.38f + breath * 0.10f),
                    Color(0xFFFFECB3).copy(alpha = 0.20f + breath * 0.06f),
                    Color(0xFFFFE082).copy(alpha = 0.08f + breath * 0.03f),
                    Color.Transparent,
                ),
                center = sunCenter,
                radius = w * 0.72f,
            ),
            radius = w * 0.72f,
            center = sunCenter,
        )

        // 2. 破云斜射透光光柱（明显而柔和的丁达尔天光）
        val godRayAlpha = (0.16f + breath * 0.06f).coerceIn(0f, 0.25f)
        val rayPath1 = Path().apply {
            moveTo(sunCenter.x - 45f * density, sunCenter.y)
            lineTo(sunCenter.x + 25f * density, sunCenter.y)
            lineTo(w * 0.15f, h * 0.58f)
            lineTo(w * -0.05f, h * 0.54f)
            close()
        }
        drawPath(
            path = rayPath1,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFDE7).copy(alpha = godRayAlpha),
                    Color.Transparent,
                ),
                start = sunCenter,
                end = Offset(w * 0.06f, h * 0.56f),
            ),
        )

        val rayPath2 = Path().apply {
            moveTo(sunCenter.x - 15f * density, sunCenter.y + 10f * density)
            lineTo(sunCenter.x + 50f * density, sunCenter.y + 10f * density)
            lineTo(w * 0.58f, h * 0.68f)
            lineTo(w * 0.42f, h * 0.65f)
            close()
        }
        drawPath(
            path = rayPath2,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFF9C4).copy(alpha = godRayAlpha * 0.90f),
                    Color.Transparent,
                ),
                start = sunCenter,
                end = Offset(w * 0.50f, h * 0.66f),
            ),
        )

        // 3. 全局多层次自然漫游白云群（覆盖上空、中空与地面上方，全屏皆有生动可见的浮云漫步）
        // 云群 1：上空主积云（清晰饱满，舒缓自左向右漂移）
        val c1Width = w * 1.10f
        val c1Period = w + c1Width + 180f * density
        val c1X = ((timeSeconds * 18f * density + c1Period * 0.30f) % c1Period) - c1Width * 0.5f
        val c1Y = 120f * density
        drawSoftCloudCluster(
            centerX = c1X,
            centerY = c1Y,
            baseWidth = c1Width,
            baseHeight = 160f * density,
            alpha = 0.62f,
            time = timeSeconds,
            morphOffset = 0f,
            isNight = isNight,
        )

        // 云群 2：中空横贯云带（穿行于城市指示标与地图腹地，非常显眼）
        val c2Width = w * 1.05f
        val c2Period = w + c2Width + 200f * density
        val c2X = (((timeSeconds * 22f * density) + c2Period * 0.72f) % c2Period) - c2Width * 0.5f
        val c2Y = 240f * density
        drawSoftCloudCluster(
            centerX = c2X,
            centerY = c2Y,
            baseWidth = c2Width,
            baseHeight = 150f * density,
            alpha = 0.58f,
            time = timeSeconds + 12f,
            morphOffset = 2.4f,
            isNight = isNight,
        )

        // 云群 3：低空飘拂积云（掠过中下部地表，形成完整的大气景深）
        val c3Width = w * 0.95f
        val c3Period = w + c3Width + 160f * density
        val c3X = (((timeSeconds * 20f * density) + c3Period * 0.12f) % c3Period) - c3Width * 0.5f
        val c3Y = 380f * density
        drawSoftCloudCluster(
            centerX = c3X,
            centerY = c3Y,
            baseWidth = c3Width,
            baseHeight = 135f * density,
            alpha = 0.52f,
            time = timeSeconds + 20f,
            morphOffset = 4.1f,
            isNight = isNight,
        )

        // 云群 4：下方近景羽状流云（轻快飘逸，靠近底部主按钮上方）
        val c4Width = w * 0.85f
        val c4Period = w + c4Width + 140f * density
        val c4X = (((timeSeconds * 26f * density) + c4Period * 0.50f) % c4Period) - c4Width * 0.5f
        val c4Y = 520f * density
        drawSoftCloudCluster(
            centerX = c4X,
            centerY = c4Y,
            baseWidth = c4Width,
            baseHeight = 115f * density,
            alpha = 0.44f,
            time = timeSeconds + 32f,
            morphOffset = 1.6f,
            isNight = isNight,
        )

        // 云群 5：高空轻灵游云（飘拂于顶部状态栏下方）
        val c5Width = w * 0.80f
        val c5Period = w + c5Width + 130f * density
        val c5X = (((timeSeconds * 24f * density) + c5Period * 0.88f) % c5Period) - c5Width * 0.5f
        val c5Y = 55f * density
        drawSoftCloudCluster(
            centerX = c5X,
            centerY = c5Y,
            baseWidth = c5Width,
            baseHeight = 95f * density,
            alpha = 0.48f,
            time = timeSeconds + 8f,
            morphOffset = 3.3f,
            isNight = isNight,
        )
    }
}

/* ========================================================================= */
/*                     5. 阴天：沉稳厚重低气压天幕系统 (☁️ OVERCAST)          */
/* ========================================================================= */

/**
 * 阴天渲染：摒弃生硬贴顶的灰色闭合多边形，
 * 采用全屏均匀平滑的冷调漫射天光 + 舒缓流动的高空厚层层积云，
 * 营造沉静、真实、通透的阴天气压感，绝不产生污浊灰色块或突兀分界线。
 */
@Composable
private fun CinematicOvercastAtmosphereCanvas() {
    var frameTicker by remember { mutableLongStateOf(0L) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val delta = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos
                timeSeconds += delta
                frameTicker = nowNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val density = density

        // 1. 全局阴天冷光氛围漫射层（平滑垂直渐变，无任何横切线条或突兀分界）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF37474F).copy(alpha = 0.12f),
                    Color(0xFF455A64).copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = h * 0.70f,
            ),
            size = size,
        )

        // 2. 悬浮式冷调层积云群（银灰与珍珠白柔和羽化交叠，零硬边）
        // 上空主厚云层
        val c1Width = w * 1.15f
        val c1Period = w + c1Width + 180f * density
        val c1X = ((timeSeconds * 8.5f * density + c1Period * 0.25f) % c1Period) - c1Width * 0.5f
        drawOvercastCloudCluster(
            centerX = c1X,
            centerY = 110f * density,
            baseWidth = c1Width,
            baseHeight = 150f * density,
            alpha = 0.52f,
            time = timeSeconds,
        )

        // 中空厚积云层
        val c2Width = w * 1.05f
        val c2Period = w + c2Width + 200f * density
        val c2X = (((timeSeconds * 11.5f * density) + c2Period * 0.68f) % c2Period) - c2Width * 0.5f
        drawOvercastCloudCluster(
            centerX = c2X,
            centerY = 220f * density,
            baseWidth = c2Width,
            baseHeight = 140f * density,
            alpha = 0.46f,
            time = timeSeconds + 15f,
        )

        // 低空平缓游云
        val c3Width = w * 0.90f
        val c3Period = w + c3Width + 150f * density
        val c3X = (((timeSeconds * 14.0f * density) + c3Period * 0.05f) % c3Period) - c3Width * 0.5f
        drawOvercastCloudCluster(
            centerX = c3X,
            centerY = 350f * density,
            baseWidth = c3Width,
            baseHeight = 110f * density,
            alpha = 0.38f,
            time = timeSeconds + 28f,
        )
    }
}

/**
 * 绘制阴天专用的冷感银白羽化积云团。
 */
private fun DrawScope.drawOvercastCloudCluster(
    centerX: Float,
    centerY: Float,
    baseWidth: Float,
    baseHeight: Float,
    alpha: Float,
    time: Float,
) {
    if (alpha <= 0.005f) return

    val breathe = sin(time * 0.30f) * 0.05f
    val puffWidth = baseWidth * (1f + breathe)
    val puffHeight = baseHeight * (1f - breathe * 0.3f)

    val puffs = listOf(
        CloudPuffDef(0.0f, 0.0f, 0.42f, 2.7f, 0.95f, 1.0f),
        CloudPuffDef(-0.16f, -0.20f, 0.34f, 2.3f, 1.05f, 0.90f),
        CloudPuffDef(0.18f, -0.18f, 0.36f, 2.4f, 1.00f, 0.92f),
        CloudPuffDef(-0.35f, 0.02f, 0.30f, 2.8f, 0.85f, 0.78f),
        CloudPuffDef(0.38f, 0.04f, 0.32f, 2.9f, 0.80f, 0.75f),
        CloudPuffDef(-0.10f, 0.16f, 0.34f, 3.1f, 0.75f, 0.82f),
        CloudPuffDef(0.14f, 0.14f, 0.32f, 3.0f, 0.75f, 0.80f),
    )

    for (p in puffs) {
        val px = centerX + p.dx * puffWidth
        val py = centerY + p.dy * puffHeight
        val radius = puffHeight * p.radiusFraction
        val currentAlpha = (alpha * p.alphaFactor).coerceIn(0f, 1f)

        withTransform({
            scale(scaleX = p.scaleX, scaleY = p.scaleY, pivot = Offset(px, py))
        }) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFFECEFF1).copy(alpha = currentAlpha),
                    0.38f to Color(0xFFCFD8DC).copy(alpha = currentAlpha * 0.80f),
                    0.68f to Color(0xFFB0BEC5).copy(alpha = currentAlpha * 0.38f),
                    0.88f to Color(0xFFECEFF1).copy(alpha = currentAlpha * 0.10f),
                    1.0f to Color.Transparent,
                    center = Offset(px, py),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(px, py),
            )
        }
    }
}

/* ========================================================================= */
/*                     6. 雾霾与大雾层次系统 (🌫️ FOG / HAZE)                 */
/* ========================================================================= */

/**
 * 大雾/平流雾渲染系统：
 * 彻底移除生硬移动的大灰圆，改用全屏高阶羽化平流雾缕（Advection Fog Ribbon）。
 * 多道横向绵延、边缘无限衰减的柔美雾汽在平原与山野间轻盈游走，呈现空灵写意的高铁旅程意境。
 */
@Composable
private fun CinematicMistCanvas(
    ribbonAlpha: Float = 0.35f,
    ambientAlpha: Float = 0.12f,
    tintColor: Color = Color(0xFFECEFF1),
) {
    var frameTicker by remember { mutableLongStateOf(0L) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val delta = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos
                timeSeconds += delta
                frameTicker = nowNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val density = density

        // 1. 全局轻柔薄雾微光晕层（轻透呼吸感，完全无色块边缘）
        val breath = (sin(timeSeconds * 0.4f) + 1f) * 0.5f
        val currentAmbient = (ambientAlpha * (0.85f + breath * 0.30f)).coerceIn(0f, 0.4f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tintColor.copy(alpha = currentAmbient),
                    tintColor.copy(alpha = currentAmbient * 0.65f),
                    tintColor.copy(alpha = currentAmbient * 0.20f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = h * 0.85f,
            ),
            size = size,
        )

        // 2. 飘逸平流山岚雾带（横向拉伸极宽，四向渐隐，绝无几何圆形或硬边）
        // 雾缕 1：上空游走轻岚
        drawMistRibbon(
            centerX = (((timeSeconds * 12f * density) + w * 0.3f) % (w * 2.2f)) - w * 0.6f,
            centerY = 130f * density,
            width = w * 1.8f,
            height = 130f * density,
            alpha = ribbonAlpha * 0.90f,
            tintColor = tintColor,
            time = timeSeconds,
        )

        // 雾缕 2：中景掠过原野的深远晨雾
        drawMistRibbon(
            centerX = (((timeSeconds * 8.5f * density) + w * 1.2f) % (w * 2.2f)) - w * 0.6f,
            centerY = 260f * density,
            width = w * 2.0f,
            height = 160f * density,
            alpha = ribbonAlpha,
            tintColor = tintColor,
            time = timeSeconds + 8f,
        )

        // 雾缕 3：低空贴地平流雾（在主按钮上方虚虚弥漫）
        drawMistRibbon(
            centerX = (((timeSeconds * 15f * density) + w * 0.8f) % (w * 2.2f)) - w * 0.6f,
            centerY = 420f * density,
            width = w * 1.6f,
            height = 140f * density,
            alpha = ribbonAlpha * 0.85f,
            tintColor = tintColor,
            time = timeSeconds + 16f,
        )
    }
}

/**
 * 绘制单道超柔横向羽化平流雾缕（Advection Fog Ribbon）。
 * 四方完全平滑衰减，无任何硬边缘或球体圆圈感。
 */
private fun DrawScope.drawMistRibbon(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    alpha: Float,
    tintColor: Color,
    time: Float,
) {
    if (alpha <= 0.005f) return

    val wave = sin(time * 0.45f) * height * 0.10f
    val actualY = centerY + wave

    // 采用横向极大倍率拉伸的径向柔光带，边缘完全淡入透明
    withTransform({
        scale(scaleX = 3.6f, scaleY = 0.85f, pivot = Offset(centerX, actualY))
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.White.copy(alpha = alpha),
                0.35f to Color(0xFFF5F9FA).copy(alpha = alpha * 0.75f),
                0.65f to tintColor.copy(alpha = alpha * 0.35f),
                0.88f to tintColor.copy(alpha = alpha * 0.08f),
                1.0f to Color.Transparent,
                center = Offset(centerX, actualY),
                radius = height * 0.65f,
            ),
            radius = height * 0.65f,
            center = Offset(centerX, actualY),
        )
    }
}

/* ========================================================================= */
/*                     7. 沙尘暴与浮尘微粒系统 (🌪️ SAND / DUST)              */
/* ========================================================================= */

private class SandGlowParticle(
    var x: Float,
    var y: Float,
    var length: Float,
    var speed: Float,
    var alpha: Float,
)

@Composable
private fun CinematicSandstormCanvas(
    isDust: Boolean = false,
) {
    val density = LocalDensity.current
    val particleCount = if (isDust) 36 else 70
    val speedScale = if (isDust) 0.65f else 1.25f
    val bgTintAlpha = if (isDust) 0.05f else 0.09f
    val sandColor = if (isDust) Color(0xFFD7CCC8) else Color(0xFFFFCC80)

    val sands = remember(isDust) {
        val rnd = Random(System.currentTimeMillis())
        List(particleCount) {
            SandGlowParticle(
                x = rnd.nextFloat() * 1800f,
                y = rnd.nextFloat() * 3000f,
                length = (rnd.nextFloat() * (if (isDust) 8f else 14f) + 6f) * density.density,
                speed = (rnd.nextFloat() * 600f + 500f) * speedScale * density.density,
                alpha = rnd.nextFloat() * (if (isDust) 0.22f else 0.35f) + 0.18f,
            )
        }
    }

    var frameTicker by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                    return@withFrameNanos
                }
                val delta = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = nowNanos

                for (s in sands) {
                    s.x += s.speed * 1.35f * delta
                    s.y += s.speed * 0.35f * delta
                }
                frameTicker = nowNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTicker < 0) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        drawRect(
            color = if (isDust) Color(0xFFBCAAA4).copy(alpha = bgTintAlpha) else Color(0xFFFFB74D).copy(alpha = bgTintAlpha),
            size = size,
        )
        for (s in sands) {
            if (s.x > w + s.length) {
                s.x = -s.length - Random.nextFloat() * 60f
                s.y = Random.nextFloat() * h
            }
            if (s.y > h) {
                s.y = 0f
            }

            drawLine(
                color = sandColor.copy(alpha = s.alpha),
                start = Offset(s.x, s.y),
                end = Offset(s.x + s.length * 1.35f, s.y + s.length * 0.35f),
                strokeWidth = 1.6f * density.density,
                cap = StrokeCap.Round,
            )
        }
    }
}
