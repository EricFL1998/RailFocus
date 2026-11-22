package com.hsr.railfocus.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 三级光影材质系统。
 * L0 纸：贴地，用于背景与地图。
 * L1 浮起：卡片与 header，有明确高度。
 * L2 玻璃：半透明 + 高光描边，用于专注页边缘控件。
 */
object RailMaterial {

    /** L1 浮起卡片 */
    @Composable
    fun FloatingCard(
        modifier: Modifier = Modifier,
        shape: RoundedCornerShape = RoundedCornerShape(28.dp),
        content: @Composable () -> Unit,
    ) {
        Surface(
            modifier = modifier.shadow(8.dp, shape),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            content = content,
        )
    }

    /** L2 玻璃表面：半透明 + 顶部高光 */
    @Composable
    fun GlassSurface(
        modifier: Modifier = Modifier,
        shape: RoundedCornerShape = RoundedCornerShape(28.dp),
        content: @Composable BoxScope.() -> Unit,
    ) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    shape = shape,
                ),
            content = content,
        )
    }

    /** 纸感噪点：只画一次，用于地图背景 */
    @Composable
    fun PaperNoise(modifier: Modifier = Modifier) {
        // 由 MapLibre 背景层承担，这里仅提供语义入口
    }
}
