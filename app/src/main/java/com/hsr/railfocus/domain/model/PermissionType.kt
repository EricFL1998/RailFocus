package com.hsr.railfocus.domain.model

import com.hsr.railfocus.R
/**
 * 应用所需权限类型
 * 根据优先级分为：P0（必需）、P1（重要）、P2（可选）
 */
enum class PermissionType(
    val androidPermission: String,
    val priority: PermissionPriority,
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: String, // Material Icon name
) {
    // P0 必需权限
    NOTIFICATIONS(
        androidPermission = "android.permission.POST_NOTIFICATIONS",
        priority = PermissionPriority.REQUIRED,
        titleRes = R.string.perm_title_notifications,
        descriptionRes = R.string.perm_desc_notifications,
        icon = "notifications",
    ),
    
    SYSTEM_ALERT_WINDOW(
        androidPermission = android.Manifest.permission.SYSTEM_ALERT_WINDOW,
        priority = PermissionPriority.REQUIRED,
        titleRes = R.string.perm_title_overlay,
        descriptionRes = R.string.perm_desc_overlay,
        icon = "picture_in_picture",
    ),
    
    // P1 重要权限
    DO_NOT_DISTURB(
        androidPermission = android.Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        priority = PermissionPriority.IMPORTANT,
        titleRes = R.string.perm_title_dnd,
        descriptionRes = R.string.perm_desc_dnd,
        icon = "do_not_disturb",
    ),
    
    USAGE_STATS(
        androidPermission = android.Manifest.permission.PACKAGE_USAGE_STATS,
        priority = PermissionPriority.IMPORTANT,
        titleRes = R.string.perm_title_usage_stats,
        descriptionRes = R.string.perm_desc_usage_stats,
        icon = "bar_chart",
    ),

    RECORD_AUDIO(
        androidPermission = android.Manifest.permission.RECORD_AUDIO,
        priority = PermissionPriority.IMPORTANT,
        titleRes = R.string.perm_title_mic,
        descriptionRes = R.string.perm_desc_mic,
        icon = "record_voice_over",
    ),
    
    LOCATION(
        androidPermission = android.Manifest.permission.ACCESS_FINE_LOCATION,
        priority = PermissionPriority.IMPORTANT,
        titleRes = R.string.perm_title_location,
        descriptionRes = R.string.perm_desc_location,
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
