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
) {
    val isVisible = condition != null && condition != WeatherCondition.UNKNOWN

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(800)),
        exit = fadeOut(tween(800)),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (condition) {
                WeatherCondition.CLEAR -> {
                    CinematicSunbeamCanvas()
                }

                WeatherCondition.CLOUDY -> {
                    CinematicCloudyCanvas()
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
                    // 中雨：标准连绵雨势，雨丝长度适中，水花常态激起
                    CinematicRainCanvas(
                        densityMultiplier = 1.05f,
                        speedMultiplier = 1.10f,
                        lengthMultiplier = 1.0f,
                        strokeMultiplier = 1.0f,
                        splashRate = 0.25f,
                        atmosphereAlpha = 0.18f,
                        windBaseSlope = -0.16f,
                        windVariation = 0.04f,
                    )
                }

                WeatherCondition.HEAVY_RAIN -> {
                    // 大雨：倾盆长雨丝，急促密集，较强水花，天幕明显加深
                    CinematicRainCanvas(
                        densityMultiplier = 1.45f,
                        speedMultiplier = 1.30f,
                        lengthMultiplier = 1.35f,
                        strokeMultiplier = 1.25f,
                        splashRate = 0.40f,
                        atmosphereAlpha = 0.26f,
                        windBaseSlope = -0.20f,
                        windVariation = 0.06f,
                    )
                }

                WeatherCondition.STORM_RAIN -> {
                    // 暴雨：密织长雨帘，极速俯冲，大风偏斜，低气压沉暗天幕
                    CinematicRainCanvas(
                        densityMultiplier = 1.85f,
                        speedMultiplier = 1.50f,
                        lengthMultiplier = 1.65f,
                        strokeMultiplier = 1.40f,
                        splashRate = 0.55f,
                        atmosphereAlpha = 0.36f,
                        windBaseSlope = -0.25f,
                        windVariation = 0.08f,
                    )
                }

                WeatherCondition.THUNDER_SHOWER -> {
                    // 雷阵雨：大暴雨雨势 + 偶发天际双闪电光
                    CinematicRainCanvas(
                        densityMultiplier = 1.65f,
                        speedMultiplier = 1.40f,
                        lengthMultiplier = 1.50f,
                        strokeMultiplier = 1.35f,
                        splashRate = 0.50f,
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

                WeatherCondition.FOG,
                WeatherCondition.HAZE -> {
                    CinematicMistCanvas()
                }

                WeatherCondition.DUST,
                WeatherCondition.SAND -> {
                    CinematicSandstormCanvas()
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
        val rnd = Random(101)
        val list = ArrayList<RainDrop>(totalDrops)
        repeat(bgCount) {
            list.add(
                RainDrop(
                    x = rnd.nextFloat() * 1600f,
                    y = rnd.nextFloat() * 2800f,
                    length = (rnd.nextFloat() * 12f + 12f) * lengthMultiplier * density.density,
                    speed = (rnd.nextFloat() * 700f + 1200f) * speedMultiplier * density.density,
                    strokeWidth = 1.2f * strokeMultiplier * density.density,
                    alpha = rnd.nextFloat() * 0.18f + 0.25f,
                    layer = 0,
                )
            )
        }
        repeat(midCount) {
            list.add(
                RainDrop(
                    x = rnd.nextFloat() * 1600f,
                    y = rnd.nextFloat() * 2800f,
                    length = (rnd.nextFloat() * 18f + 22f) * lengthMultiplier * density.density,
                    speed = (rnd.nextFloat() * 800f + 1800f) * speedMultiplier * density.density,
                    strokeWidth = 1.8f * strokeMultiplier * density.density,
                    alpha = rnd.nextFloat() * 0.25f + 0.45f,
                    layer = 1,
                )
            )
        }
        repeat(fgCount) {
            list.add(
                RainDrop(
                    x = rnd.nextFloat() * 1600f,
                    y = rnd.nextFloat() * 2800f,
                    length = (rnd.nextFloat() * 24f + 36f) * lengthMultiplier * density.density,
                    speed = (rnd.nextFloat() * 900f + 2400f) * speedMultiplier * density.density,
                    strokeWidth = 2.5f * strokeMultiplier * density.density,
                    alpha = rnd.nextFloat() * 0.2f + 0.65f,
                    layer = 2,
                )
            )
        }
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

        for (d in drops) {
            if (d.y > h) {
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
                d.y = -d.length - Random.nextFloat() * 80f
                d.x = Random.nextFloat() * (w + 240f)
            }
            if (d.x < -120f) {
                d.x = w + 80f
            }

            val slantX = d.length * windSlope
            val startOffset = Offset(d.x - slantX, d.y - d.length)
            val endOffset = Offset(d.x, d.y)

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
                strokeWidth = d.strokeWidth * 1.35f,
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
/*                     4. 多云：纯白羽化浮云与破云晨光天幕 (⛅ CLOUDY)        */
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
 * 绘制单个纯白微暖、边缘完全超柔羽化的自然积云团。
 * 彻底剔除任何灰色色值与生硬边界，由多组横向拉伸、三阶平滑衰减的柔和微晶气团交叠而成。
 */
private fun DrawScope.drawSoftCloudCluster(
    centerX: Float,
    centerY: Float,
    baseWidth: Float,
    baseHeight: Float,
    alpha: Float,
    time: Float,
    morphOffset: Float = 0f,
) {
    if (alpha <= 0.005f) return

    val breathe = sin(time * 0.35f + morphOffset) * 0.06f
    val puffWidth = baseWidth * (1f + breathe)
    val puffHeight = baseHeight * (1f - breathe * 0.4f)

    val puffs = listOf(
        // 主核中心丰满云团
        CloudPuffDef(0.0f, 0.0f, 0.42f, 2.6f, 0.95f, 1.0f),
        // 顶部受光微凸起（纯白透光）
        CloudPuffDef(-0.16f, -0.22f, 0.32f, 2.3f, 1.05f, 0.88f),
        CloudPuffDef(0.18f, -0.20f, 0.34f, 2.4f, 1.00f, 0.92f),
        // 侧翼自然延展云羽
        CloudPuffDef(-0.36f, 0.04f, 0.28f, 2.7f, 0.85f, 0.72f),
        CloudPuffDef(0.38f, 0.06f, 0.30f, 2.8f, 0.80f, 0.68f),
        // 底部柔和凝结过渡基底
        CloudPuffDef(-0.10f, 0.16f, 0.32f, 3.0f, 0.75f, 0.80f),
        CloudPuffDef(0.14f, 0.14f, 0.30f, 2.9f, 0.75f, 0.75f),
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
                    0.35f to Color(0xFFFFFDF9).copy(alpha = currentAlpha * 0.75f),
                    0.65f to Color(0xFFF9FBFB).copy(alpha = currentAlpha * 0.35f),
                    0.88f to Color(0xFFFFFFFF).copy(alpha = currentAlpha * 0.08f),
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
private fun CinematicCloudyCanvas() {
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

        // 1. 右上天际破云温润金光与天幕光晕
        val sunCenter = Offset(w * 0.88f, h * 0.07f)
        val breath = (sin(timeSeconds * 0.5f) + 1f) * 0.5f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF9C4).copy(alpha = 0.28f + breath * 0.06f),
                    Color(0xFFFFECB3).copy(alpha = 0.14f + breath * 0.04f),
                    Color(0xFFFFE082).copy(alpha = 0.05f + breath * 0.02f),
                    Color.Transparent,
                ),
                center = sunCenter,
                radius = w * 0.65f,
            ),
            radius = w * 0.65f,
            center = sunCenter,
        )

        // 2. 破云斜射日光柱（柔和微光，透光自然）
        val godRayAlpha = (0.07f + breath * 0.04f).coerceIn(0f, 0.15f)
        val rayPath1 = Path().apply {
            moveTo(sunCenter.x - 30f * density, sunCenter.y)
            lineTo(sunCenter.x + 15f * density, sunCenter.y)
            lineTo(w * 0.10f, h * 0.52f)
            lineTo(w * -0.05f, h * 0.48f)
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
                end = Offset(w * 0.05f, h * 0.50f),
            ),
        )

        val rayPath2 = Path().apply {
            moveTo(sunCenter.x - 10f * density, sunCenter.y + 10f * density)
            lineTo(sunCenter.x + 35f * density, sunCenter.y + 10f * density)
            lineTo(w * 0.48f, h * 0.60f)
            lineTo(w * 0.35f, h * 0.58f)
            close()
        }
        drawPath(
            path = rayPath2,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFF9C4).copy(alpha = godRayAlpha * 0.85f),
                    Color.Transparent,
                ),
                start = sunCenter,
                end = Offset(w * 0.40f, h * 0.58f),
            ),
        )

        // 3. 独立悬浮、随风自左向右轻柔漫游的纯白积云群（无顶部死板贴顶遮盖，无任何灰度色块）
        // 云群 1：高空轻灵羽云（漂移稍快，微暖轻盈）
        val c1Width = w * 0.85f
        val c1Period = w + c1Width + 160f * density
        val c1X = ((timeSeconds * 14f * density + c1Period * 0.15f) % c1Period) - c1Width * 0.5f
        val c1Y = 80f * density
        drawSoftCloudCluster(
            centerX = c1X,
            centerY = c1Y,
            baseWidth = c1Width,
            baseHeight = 120f * density,
            alpha = 0.32f,
            time = timeSeconds,
            morphOffset = 0f,
        )

        // 云群 2：中空主积云团（舒缓漫步，丰满通透）
        val c2Width = w * 1.05f
        val c2Period = w + c2Width + 200f * density
        val c2X = (((timeSeconds * 8.5f * density) + c2Period * 0.50f) % c2Period) - c2Width * 0.5f
        val c2Y = 160f * density
        drawSoftCloudCluster(
            centerX = c2X,
            centerY = c2Y,
            baseWidth = c2Width,
            baseHeight = 160f * density,
            alpha = 0.40f,
            time = timeSeconds + 10f,
            morphOffset = 2.1f,
        )

        // 云群 3：低空飘拂薄缕（轻灵低飞，空灵微散）
        val c3Width = w * 0.75f
        val c3Period = w + c3Width + 140f * density
        val c3X = (((timeSeconds * 18f * density) + c3Period * 0.85f) % c3Period) - c3Width * 0.5f
        val c3Y = 240f * density
        drawSoftCloudCluster(
            centerX = c3X,
            centerY = c3Y,
            baseWidth = c3Width,
            baseHeight = 100f * density,
            alpha = 0.25f,
            time = timeSeconds + 24f,
            morphOffset = 4.2f,
        )
    }
}

/* ========================================================================= */
/*                     5. 阴天：沉稳厚重低气压天幕系统 (☁️ OVERCAST)          */
/* ========================================================================= */

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

        // 阴天三层连续起伏冷调云幕（无任何离散几何椭圆或圆圈）
        drawOrganicCloudCanopy(
            width = w,
            baseHeight = 180.dp.toPx(),
            time = timeSeconds,
            speed = 0.14f,
            freq1 = 2.0f, amp1 = 35.dp.toPx(),
            freq2 = 4.5f, amp2 = 20.dp.toPx(),
            freq3 = 8.0f, amp3 = 10.dp.toPx(),
            gradientColors = listOf(
                Color(0x35455A64),
                Color(0x1E546E7A),
                Color.Transparent,
            ),
        )

        drawOrganicCloudCanopy(
            width = w,
            baseHeight = 310.dp.toPx(),
            time = timeSeconds + 15f,
            speed = 0.22f,
            freq1 = 1.6f, amp1 = 50.dp.toPx(),
            freq2 = 3.8f, amp2 = 26.dp.toPx(),
            freq3 = 6.8f, amp3 = 14.dp.toPx(),
            gradientColors = listOf(
                Color(0x2E546E7A),
                Color(0x1678909C),
                Color.Transparent,
            ),
        )

        drawOrganicCloudCanopy(
            width = w,
            baseHeight = 440.dp.toPx(),
            time = timeSeconds + 35f,
            speed = 0.30f,
            freq1 = 1.3f, amp1 = 60.dp.toPx(),
            freq2 = 3.0f, amp2 = 32.dp.toPx(),
            freq3 = 5.5f, amp3 = 16.dp.toPx(),
            gradientColors = listOf(
                Color(0x24607D8B),
                Color(0x0E90A4AE),
                Color.Transparent,
            ),
        )
    }
}

/**
 * 绘制连绵起伏、有机流动的连续云幕（闭合波形曲线填充 + 自然垂直渐变衰减）。
 * 绝不使用任何生硬椭圆、硬边圆圈或拼凑气泡，呈现自然天幕流云。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrganicCloudCanopy(
    width: Float,
    baseHeight: Float,
    time: Float,
    speed: Float,
    freq1: Float, amp1: Float,
    freq2: Float, amp2: Float,
    freq3: Float, amp3: Float,
    gradientColors: List<Color>,
) {
    val stepPx = 20f
    val path = Path().apply {
        moveTo(-stepPx, 0f)
        var x = -stepPx
        while (x <= width + stepPx * 2) {
            val nx = x / width
            val wave = sin(nx * freq1 + time * speed) * amp1 +
                cos(nx * freq2 - time * speed * 1.25f) * amp2 +
                sin(nx * freq3 + time * speed * 0.75f) * amp3
            val y = baseHeight + wave
            lineTo(x, y)
            x += stepPx
        }
        lineTo(width + stepPx * 2, 0f)
        close()
    }

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = gradientColors,
            startY = 0f,
            endY = baseHeight + amp1 + amp2,
        ),
    )
}

/* ========================================================================= */
/*                     6. 雾霾与薄雾层次系统 (🌫️ FOG / HAZE)                 */
/* ========================================================================= */

@Composable
private fun CinematicMistCanvas() {
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

        val drift1 = (timeSeconds * 16f) % (w * 2f)
        val drift2 = (timeSeconds * 10f) % (w * 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFCFD8DC).copy(alpha = 0.16f), Color.Transparent),
                center = Offset(drift1 - w * 0.4f, h * 0.38f),
                radius = w * 0.75f,
            ),
            radius = w * 0.75f,
            center = Offset(drift1 - w * 0.4f, h * 0.38f),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB0BEC5).copy(alpha = 0.13f), Color.Transparent),
                center = Offset(w * 1.4f - drift2, h * 0.46f),
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = Offset(w * 1.4f - drift2, h * 0.46f),
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
private fun CinematicSandstormCanvas() {
    val density = LocalDensity.current
    val sands = remember {
        val rnd = Random(88)
        List(60) {
            SandGlowParticle(
                x = rnd.nextFloat() * 1600f,
                y = rnd.nextFloat() * 2800f,
                length = (rnd.nextFloat() * 10f + 6f) * density.density,
                speed = (rnd.nextFloat() * 800f + 600f) * density.density,
                alpha = rnd.nextFloat() * 0.32f + 0.22f,
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
            color = Color(0xFFFFB74D).copy(alpha = 0.08f),
            size = size,
        )

        val sandColor = Color(0xFFFFCC80)
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
