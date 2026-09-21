package com.hsr.railfocus.data.repository

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.AppOpsManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.hsr.railfocus.domain.model.PermissionState
import com.hsr.railfocus.domain.model.PermissionStatus
import com.hsr.railfocus.domain.model.PermissionType
import com.hsr.railfocus.domain.repository.PermissionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionRepository {
    
    private val _permissionsFlow = MutableStateFlow<List<PermissionState>>(emptyList())
    
    override suspend fun checkPermission(type: PermissionType): PermissionState {
        val status = when (type) {
            PermissionType.SYSTEM_ALERT_WINDOW -> {
                if (Settings.canDrawOverlays(context)) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            }
            
            PermissionType.DO_NOT_DISTURB -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            }
            
            PermissionType.USAGE_STATS -> {
                if (hasUsageStatsPermission()) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            }

            PermissionType.NOTIFICATIONS -> {
                // POST_NOTIFICATIONS 是 API 33 才引入的运行时权限；
                // 低版本系统不认识该字符串，checkSelfPermission 会永久返回 DENIED，
                // 必须直接视为已授予，否则 API < 33 的设备永远无法通过权限门。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when (ContextCompat.checkSelfPermission(context, type.androidPermission)) {
                        PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
                        else -> PermissionStatus.DENIED
                    }
                } else {
                    PermissionStatus.GRANTED
                }
            }
        }
        
        return PermissionState(
            type = type,
            status = status,
            shouldShowRationale = false,
        )
    }
    
    override suspend fun checkAllPermissions(): List<PermissionState> {
        val permissions = PermissionType.entries.map { checkPermission(it) }
        _permissionsFlow.value = permissions
        return permissions
    }
    
    override fun observePermissions(): Flow<List<PermissionState>> {
        return _permissionsFlow.asStateFlow()
    }
    
    override suspend fun shouldShowRationale(type: PermissionType): Boolean {
        // 特殊权限不需要 rationale
        if (type in listOf(
                PermissionType.SYSTEM_ALERT_WINDOW,
                PermissionType.DO_NOT_DISTURB,
                PermissionType.USAGE_STATS
            )
        ) {
            return false
        }
        
        // 普通权限的 rationale 需要在 Activity 中检查
        // 这里返回 false，实际逻辑在 UI 层
        return false
    }
    
    override suspend fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    override suspend fun checkSpecialPermission(type: PermissionType): Boolean {
        return checkPermission(type).status == PermissionStatus.GRANTED
    }
    
    override suspend fun requestSpecialPermission(type: PermissionType) {
        val intent = when (type) {
            PermissionType.SYSTEM_ALERT_WINDOW -> {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
            }
            
            PermissionType.DO_NOT_DISTURB -> {
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
            
            PermissionType.USAGE_STATS -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
            
            else -> null
        }
        
        intent?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(it)
        }
    }
    
    /**
     * 检查应用使用统计权限
     */
    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val mode = AppOpsManagerCompat.noteOpNoThrow(
                context,
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManagerCompat.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }
}
