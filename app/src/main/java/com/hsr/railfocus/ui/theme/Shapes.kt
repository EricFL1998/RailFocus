package com.hsr.railfocus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rail Focus Shapes - Material 3 Expressive
 * 更具表现力的圆角形状系统，趋向于更圆润和有机的感觉
 */
val RailShapes = Shapes(
    // 极小组件
    extraSmall = RoundedCornerShape(8.dp),

    // 小组件：按钮、Chip - 更加圆润
    small = RoundedCornerShape(16.dp),
    
    // 中等组件：卡片 - 使用更明显的圆角以体现 Expressive 风格
    medium = RoundedCornerShape(28.dp),
    
    // 大组件：对话框、底部表单
    large = RoundedCornerShape(36.dp),
    
    // 超大组件：全屏模态
    extraLarge = RoundedCornerShape(48.dp),
)
