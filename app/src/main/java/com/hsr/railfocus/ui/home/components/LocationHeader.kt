package com.hsr.railfocus.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hsr.railfocus.R

/**
 * 首页左上角位置信息头部组件
 *
 * 显示问候语、城市名称、车站名称
 * Material 3 Expressive 风格：圆角 Surface、半透明背景、投影
 */
@Composable
fun LocationHeader(
    greeting: String,
    cityName: String,
    stationName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium, // 使用中等圆角 (28.dp)
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 位置图标
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 问候语
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 城市 + 车站
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = cityName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BaselineAlignedIcon(
                        painterResId = R.drawable.ic_bullet_train,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 16.dp,
                    )
                    Text(
                        text = stationName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 将图标包裹在提供 FirstBaseline 对齐线的 Layout 中，
 * 使其与相邻文字基线对齐。
 */
@Composable
private fun BaselineAlignedIcon(
    painterResId: Int,
    contentDescription: String?,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier.size(size),
        content = {
            Image(
                painter = painterResource(id = painterResId),
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.fillMaxSize(),
            )
        },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints)
        layout(
            placeable.width,
            placeable.height,
            alignmentLines = mapOf(FirstBaseline to placeable.height),
        ) {
            placeable.placeRelative(0, 0)
        }
    }
}


