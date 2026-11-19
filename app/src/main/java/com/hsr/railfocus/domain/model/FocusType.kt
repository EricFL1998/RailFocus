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
    // 通知筛选设置
    val allowNotifications: Boolean = true,
    val allowedNotificationCategories: String = "IM,WORK,SYSTEM",
    val muteNotificationPreview: Boolean = false,
    // 勿扰模式设置：OFF(关闭), PRIORITY(仅优先), ALARMS_ONLY(仅闹钟), TOTAL_SILENCE(全面静音)
    val dndMode: String = "PRIORITY",
    // 通话过滤设置：EVERYONE(所有人), CONTACTS(通讯录), STARRED(特别关注), NONE(无)
    val callFilter: String = "STARRED",
    val allowRepeatedCalls: Boolean = true,
    val autoReplySms: Boolean = false,
    val keepScreenOn: Boolean = true,
    // 应用选择器：逗号分隔的允许通知的包名列表，如 "com.tencent.mm,com.alibaba.android.rimet"
    val allowedAppPackages: String = "",
) {
    fun toEntity(order: Int = 0): FocusTypeEntity = FocusTypeEntity(
        id = id,
        displayName = displayName,
        iconName = iconName,
        colorHex = colorHex,
        containerColorHex = containerColorHex,
        isRemovable = isRemovable,
        order = order,
        allowNotifications = allowNotifications,
        allowedNotificationCategories = allowedNotificationCategories,
        muteNotificationPreview = muteNotificationPreview,
        dndMode = dndMode,
        callFilter = callFilter,
        allowRepeatedCalls = allowRepeatedCalls,
        autoReplySms = autoReplySms,
        keepScreenOn = keepScreenOn,
        allowedAppPackages = allowedAppPackages,
    )

    companion object {
        fun fromEntity(entity: FocusTypeEntity): FocusType = FocusType(
            id = entity.id,
            displayName = entity.displayName,
            iconName = entity.iconName,
            colorHex = entity.colorHex,
            containerColorHex = entity.containerColorHex,
            isRemovable = entity.isRemovable,
            allowNotifications = entity.allowNotifications,
            allowedNotificationCategories = entity.allowedNotificationCategories,
            muteNotificationPreview = entity.muteNotificationPreview,
            dndMode = entity.dndMode,
            callFilter = entity.callFilter,
            allowRepeatedCalls = entity.allowRepeatedCalls,
            autoReplySms = entity.autoReplySms,
            keepScreenOn = entity.keepScreenOn,
            allowedAppPackages = entity.allowedAppPackages,
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
