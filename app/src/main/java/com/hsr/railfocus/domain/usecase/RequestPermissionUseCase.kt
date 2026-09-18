package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.domain.model.PermissionType
import com.hsr.railfocus.domain.repository.PermissionRepository
import javax.inject.Inject

/**
 * 请求单个权限
 * 根据权限类型自动处理普通权限或特殊权限
 */
class RequestPermissionUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository,
) {
    suspend operator fun invoke(type: PermissionType) {
        when (type) {
            PermissionType.SYSTEM_ALERT_WINDOW,
            PermissionType.USAGE_STATS,
            PermissionType.DO_NOT_DISTURB,
            -> {
                // 特殊权限需要跳转到设置页
                permissionRepository.requestSpecialPermission(type)
            }
            else -> {
                // 普通权限通过 Activity Result API 请求
                // 实际请求逻辑在 UI 层处理
                // 这里只是标记需要请求
            }
        }
    }
}
