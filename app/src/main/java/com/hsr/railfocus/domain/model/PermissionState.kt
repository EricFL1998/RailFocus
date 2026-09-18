package com.hsr.railfocus.domain.model

/**
 * 权限状态
 */
data class PermissionState(
    val type: PermissionType,
    val status: PermissionStatus,
    val shouldShowRationale: Boolean = false,
)

/**
 * 权限授予状态
 */
enum class PermissionStatus {
    GRANTED,        // 已授予
    DENIED,         // 被拒绝
}

/**
 * 所有权限的综合状态
 */
data class PermissionsOverview(
    val permissions: List<PermissionState>,
    val allRequiredGranted: Boolean,
    val allImportantGranted: Boolean,
) {
    companion object {
        fun from(permissions: List<PermissionState>): PermissionsOverview {
            val required = permissions.filter { it.type.priority == PermissionPriority.REQUIRED }
            val important = permissions.filter { it.type.priority == PermissionPriority.IMPORTANT }
            
            return PermissionsOverview(
                permissions = permissions,
                allRequiredGranted = required.all { it.status == PermissionStatus.GRANTED },
                allImportantGranted = important.all { it.status == PermissionStatus.GRANTED },
            )
        }
    }
}
