package com.hsr.railfocus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Rail Focus Color Palette - Material 3 Expressive
 * 绿色主题配色方案，灵感来自铁路与自然
 */
object RailColors {
    // Primary Colors - 铁路绿
    val Primary = Color(0xFF2E7D32)           // 深绿色 - 主色调
    val OnPrimary = Color(0xFFFFFFFF)         // 白色文字
    val PrimaryContainer = Color(0xFFA5D6A7)  // 浅绿色容器
    val OnPrimaryContainer = Color(0xFF1B5E20)// 深绿色文字

    // Secondary Colors - 辅助色
    val Secondary = Color(0xFF66BB6A)         // 中绿色
    val OnSecondary = Color(0xFFFFFFFF)       // 白色文字
    val SecondaryContainer = Color(0xFFC8E6C9)// 极浅绿色
    val OnSecondaryContainer = Color(0xFF1B5E20)

    // Tertiary Colors - 第三色（铁轨棕色）
    val Tertiary = Color(0xFF8D6E63)          // 棕色
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFD7CCC8)
    val OnTertiaryContainer = Color(0xFF4E342E)

    // Error Colors
    val Error = Color(0xFFD32F2F)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFCDD2)
    val OnErrorContainer = Color(0xFFB71C1C)

    // Background & Surface
    val Background = Color(0xFFF5F5F5)        // 浅灰背景
    val OnBackground = Color(0xFF212121)      // 深灰文字
    val Surface = Color(0xFFFFFFFF)           // 白色表面
    val OnSurface = Color(0xFF212121)         // 深灰文字
    val SurfaceVariant = Color(0xFFE8F5E9)    // 淡绿色表面变体
    val OnSurfaceVariant = Color(0xFF424242)  // 中灰文字

    // Dark Mode Specific Colors（带铁路绿暗色调性，避免死黑）
    val DarkBackground = Color(0xFF0D130F)    // 极暗绿黑背景
    val DarkSurface = Color(0xFF151D18)       // 深绿灰表面
    val DarkSurfaceVariant = Color(0xFF1F2A23) // 绿调表面变体，卡片层次
    val DarkPrimaryContainer = Color(0xFF1B5E20)
    val DarkOnPrimaryContainer = Color(0xFFA5D6A7)
    val DarkSecondaryContainer = Color(0xFF388E3C)
    val DarkOnSecondaryContainer = Color(0xFFC8E6C9)
    val DarkErrorContainer = Color(0xFFB71C1C)
    val DarkOnErrorContainer = Color(0xFFFFCDD2)

    // Outline & Other
    val Outline = Color(0xFFBDBDBD)           // 边框灰色
    val OutlineVariant = Color(0xFFE0E0E0)    // 淡边框
    val Scrim = Color(0x80000000)             // 半透明黑色遮罩

    // Surface Tint
    val SurfaceTint = Primary

    // Inverse Colors
    val InverseSurface = Color(0xFF2E2E2E)
    val InverseOnSurface = Color(0xFFF5F5F5)
    val InversePrimary = Color(0xFF81C784)
}
