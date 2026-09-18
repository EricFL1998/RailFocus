package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.domain.model.PermissionsOverview
import com.hsr.railfocus.domain.repository.PermissionRepository
import javax.inject.Inject

/**
 * 检查所有权限状态
 */
class CheckPermissionsUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository,
) {
    suspend operator fun invoke(): PermissionsOverview {
        val permissions = permissionRepository.checkAllPermissions()
        return PermissionsOverview.from(permissions)
    }
}
