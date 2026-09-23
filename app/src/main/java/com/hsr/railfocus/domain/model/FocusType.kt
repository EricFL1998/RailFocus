package com.hsr.railfocus.domain.model

import com.hsr.railfocus.data.local.entity.FocusTypeEntity

/**
 * 专注类型领域模型
 * 纯 Kotlin 领域模型，解耦 UI 渲染库
 */
data class FocusType(
    val id: String,
    val displayName: String,
    val iconName: String,
    val colorHex: Int,
    val containerColorHex: Int,
    val isRemovable: Boolean = true,
) {
    fun toEntity(order: Int = 0): FocusTypeEntity = FocusTypeEntity(
        id = id,
        displayName = displayName,
        iconName = iconName,
        colorHex = colorHex,
        containerColorHex = containerColorHex,
        isRemovable = isRemovable,
        order = order,
    )

    companion object {
        fun fromEntity(entity: FocusTypeEntity): FocusType = FocusType(
            id = entity.id,
            displayName = entity.displayName,
            iconName = entity.iconName,
            colorHex = entity.colorHex,
            containerColorHex = entity.containerColorHex,
            isRemovable = entity.isRemovable,
        )

        // 默认预设（颜色为 ARGB Int）
        val DEFAULT_LIST = listOf(
            FocusType("code", "编程", "Code", 0xFFAD1457.toInt(), 0xFFF8BBD0.toInt()),
            FocusType("learn", "学习", "Learn", 0xFF2E7D32.toInt(), 0xFFC8E6C9.toInt()),
            FocusType("work", "工作", "Work", 0xFF1565C0.toInt(), 0xFFBBDEFB.toInt()),
            FocusType("read", "阅读", "Read", 0xFF33691E.toInt(), 0xFFDCEDC8.toInt()),
            FocusType("research", "研究", "Research", 0xFF4527A0.toInt(), 0xFFD1C4E9.toInt()),
            FocusType("other", "其他", "Other", 0xFF424242.toInt(), 0xFFF5F5F5.toInt(), isRemovable = false),
        )
    }
}
