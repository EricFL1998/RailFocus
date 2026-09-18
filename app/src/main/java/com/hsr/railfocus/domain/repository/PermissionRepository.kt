package com.hsr.railfocus.domain.repository

import com.hsr.railfocus.domain.model.PermissionState
import com.hsr.railfocus.domain.model.PermissionType
import kotlinx.coroutines.flow.Flow

/**
 * 权限管理仓库接口
 * 提供权限检查、请求和状态监听功能
 */
interface PermissionRepository {
    /**
     * 检查单个权限状态
     */
    suspend fun checkPermission(type: PermissionType): PermissionState
    
    /**
     * 检查所有权限状态
     */
    suspend fun checkAllPermissions(): List<PermissionState>
    
    /**
     * 监听权限状态变化
     */
    fun observePermissions(): Flow<List<PermissionState>>
    
    /**
     * 检查是否需要显示权限说明（Rationale）
     */
    suspend fun shouldShowRationale(type: PermissionType): Boolean
    
    /**
     * 打开应用设置页面
     */
    suspend fun openAppSettings()
    
    /**
     * 检查特殊权限（需要跳转设置页）
     */
    suspend fun checkSpecialPermission(type: PermissionType): Boolean
    
    /**
     * 请求特殊权限（跳转设置页）
     */
    suspend fun requestSpecialPermission(type: PermissionType)
}
