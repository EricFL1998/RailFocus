package com.hsr.railfocus.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings

/**
 * Rail Focus 统一动效 token。
 * 只允许使用这里定义的时长与曲线，避免各组件各自为政。
 */
object Motion {
    const val Instant = 90
    const val Fast = 160
    const val Base = 240
    const val Scene = 480
    const val Stagger = 28

    /** 按压回弹：带轻微过冲，手感弹而不软 */
    val press = spring<Float>(dampingRatio = 0.55f, stiffness = 900f)

    /** 吸附归位：卡片、滑块、列表项 */
    val settle = spring<Float>(dampingRatio = 0.78f, stiffness = 420f)

    /** 呼吸、怠速、跟随 */
    val float = spring<Float>(dampingRatio = 0.9f, stiffness = 180f)

    fun <T> enter() = tween<T>(Base, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    fun <T> scene() = tween<T>(Scene, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
}

/** 系统「移除动画」开关。true 时所有装饰动画应降级为静态或极短淡入。 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scale = remember { androidx.compose.runtime.mutableFloatStateOf(readAnimatorScale(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scale.floatValue = readAnimatorScale(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        scale.floatValue = readAnimatorScale(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return scale.floatValue == 0f
}

private fun readAnimatorScale(context: android.content.Context): Float =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )

/**
 * 统一按压反馈：0.965 缩放 + 过冲回弹。
 * 替换各组件散落的 scale 逻辑。
 */
fun Modifier.pressScale(
    source: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (reduceMotion) tween(Motion.Instant) else Motion.press,
        label = "press_scale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
