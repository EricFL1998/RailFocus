package com.hsr.railfocus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val RailLightColorScheme = lightColorScheme(
    primary = RailColors.Primary,
    onPrimary = RailColors.OnPrimary,
    primaryContainer = RailColors.PrimaryContainer,
    onPrimaryContainer = RailColors.OnPrimaryContainer,

    secondary = RailColors.Secondary,
    onSecondary = RailColors.OnSecondary,
    secondaryContainer = RailColors.SecondaryContainer,
    onSecondaryContainer = RailColors.OnSecondaryContainer,

    tertiary = RailColors.Tertiary,
    onTertiary = RailColors.OnTertiary,
    tertiaryContainer = RailColors.TertiaryContainer,
    onTertiaryContainer = RailColors.OnTertiaryContainer,

    error = RailColors.Error,
    onError = RailColors.OnError,
    errorContainer = RailColors.ErrorContainer,
    onErrorContainer = RailColors.OnErrorContainer,

    background = RailColors.Background,
    onBackground = RailColors.OnBackground,
    surface = RailColors.Surface,
    onSurface = RailColors.OnSurface,
    surfaceVariant = RailColors.SurfaceVariant,
    onSurfaceVariant = RailColors.OnSurfaceVariant,

    outline = RailColors.Outline,
    outlineVariant = RailColors.OutlineVariant,
    scrim = RailColors.Scrim,
    surfaceTint = RailColors.SurfaceTint,

    inverseSurface = RailColors.InverseSurface,
    inverseOnSurface = RailColors.InverseOnSurface,
    inversePrimary = RailColors.InversePrimary,
)

private val RailDarkColorScheme = darkColorScheme(
    primary = RailColors.InversePrimary,
    onPrimary = RailColors.OnPrimaryContainer,
    primaryContainer = RailColors.DarkPrimaryContainer,
    onPrimaryContainer = RailColors.DarkOnPrimaryContainer,

    secondary = RailColors.Secondary,
    onSecondary = RailColors.OnSecondary,
    secondaryContainer = RailColors.DarkSecondaryContainer,
    onSecondaryContainer = RailColors.DarkOnSecondaryContainer,

    tertiary = RailColors.Tertiary,
    onTertiary = RailColors.OnTertiary,
    tertiaryContainer = RailColors.TertiaryContainer.copy(alpha = 0.5f),
    onTertiaryContainer = RailColors.OnTertiaryContainer,

    error = RailColors.Error,
    onError = RailColors.OnError,
    errorContainer = RailColors.DarkErrorContainer,
    onErrorContainer = RailColors.DarkOnErrorContainer,

    background = RailColors.DarkBackground,
    onBackground = RailColors.InverseOnSurface,
    surface = RailColors.DarkSurface,
    onSurface = RailColors.InverseOnSurface,
    surfaceVariant = RailColors.DarkSurfaceVariant,
    onSurfaceVariant = RailColors.InverseOnSurface.copy(alpha = 0.7f),

    outline = RailColors.Outline,
    outlineVariant = RailColors.OutlineVariant.copy(alpha = 0.2f),
    scrim = RailColors.Scrim,
    surfaceTint = RailColors.Primary, // 恢复色调着色，让 elevation 带出绿色光泽

    inverseSurface = RailColors.Surface,
    inverseOnSurface = RailColors.OnSurface,
    inversePrimary = RailColors.Primary,
)

/**
 * Rail Focus Theme
 * Material 3 Expressive Design System
 */
@Composable
fun RailFocusTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true, // 默认开启动态色彩
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // 深色模式固定使用品牌深色（带铁路绿调性）；
            // 动态深色偏灰暗，与活力目标不符
            if (darkTheme) RailDarkColorScheme else dynamicLightColorScheme(context)
        }
        darkTheme -> RailDarkColorScheme
        else -> RailLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RailTypography,
        shapes = RailShapes,
        content = {
            // Compose 默认的 LocalContentColor 是纯黑，深色模式下所有
            // "没写颜色的文字/图标"（如 surface.copy(alpha) 容器内的内容，
            // contentColorFor 对复制色会返回 Unspecified 而回退到此处）会不可读。
            // 在主题根部提供语义正确的默认内容色。
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onBackground,
                content = content,
            )
        },
    )
}
