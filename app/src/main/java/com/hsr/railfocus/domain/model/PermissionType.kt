package com.hsr.railfocus.domain.model

/**
 * 应用所需权限类型
 * 根据优先级分为：P0（必需）、P1（重要）、P2（可选）
 */
enum class PermissionType(
    val androidPermission: String,
    val priority: PermissionPriority,
    val title: String,
    val description: String,
    val icon: String, // Material Icon name
) {
    // P0 必需权限
    NOTIFICATIONS(
        androidPermission = "android.permission.POST_NOTIFICATIONS",
        priority = PermissionPriority.REQUIRED,
        title = "通知权限",
        description = "用于在专注完成时提醒您，确保不会错过重要提醒",
        icon = "notifications",
    ),
    
    SYSTEM_ALERT_WINDOW(
        androidPermission = android.Manifest.permission.SYSTEM_ALERT_WINDOW,
        priority = PermissionPriority.REQUIRED,
        title = "悬浮窗权限",
        description = "用于在其他应用上方显示专注计时器，随时查看进度",
        icon = "picture_in_picture",
    ),
    
    // P1 重要权限
    DO_NOT_DISTURB(
        androidPermission = android.Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        priority = PermissionPriority.IMPORTANT,
        title = "勿扰模式权限",
        description = "专注时自动开启勿扰模式，避免打扰",
        icon = "do_not_disturb",
    ),
    
    USAGE_STATS(
        androidPermission = android.Manifest.permission.PACKAGE_USAGE_STATS,
        priority = PermissionPriority.IMPORTANT,
        title = "应用使用统计",
        description = "检测您是否使用分心应用，帮助保持专注",
        icon = "bar_chart",
    ),

    RECORD_AUDIO(
        androidPermission = android.Manifest.permission.RECORD_AUDIO,
        priority = PermissionPriority.IMPORTANT,
        title = "麦克风权限",
        description = "用于在旅行手账中录制语音留念，封存当年的原声",
        icon = "record_voice_over",
    ),
    
    LOCATION(
        androidPermission = android.Manifest.permission.ACCESS_FINE_LOCATION,
        priority = PermissionPriority.IMPORTANT,
        title = "位置权限",
        description = "用于定位当前所在车站，推荐附近可到达的目的地",
        icon = "location_on",
    );
}

/**
 * 权限优先级
 */
enum class PermissionPriority {
    REQUIRED,   // P0 必需：没有此权限应用核心功能无法使用
    IMPORTANT,  // P1 重要：没有此权限体验显著下降
}
