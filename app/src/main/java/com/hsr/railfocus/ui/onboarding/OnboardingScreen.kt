package com.hsr.railfocus.ui.onboarding

import androidx.core.net.toUri

import android.content.Intent

import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.domain.model.PermissionPriority
import com.hsr.railfocus.domain.model.PermissionState
import com.hsr.railfocus.domain.model.PermissionStatus
import com.hsr.railfocus.domain.model.PermissionType

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // 通知 ViewModel 权限结果
        viewModel.checkPermissions()
    }
    
    // 特殊权限请求启动器（跳转设置页）
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 从设置页返回后重新检查权限
        viewModel.checkPermissions()
    }
    
    when (val state = uiState) {
        is OnboardingUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        is OnboardingUiState.PermissionsRequired -> {
            OnboardingContent(
                overview = state.overview,
                onRequestPermission = { type ->
                    when (type) {
                        PermissionType.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        PermissionType.SYSTEM_ALERT_WINDOW -> {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri(),
                            )
                            settingsLauncher.launch(intent)
                        }
                        PermissionType.DO_NOT_DISTURB -> {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            settingsLauncher.launch(intent)
                        }
                        PermissionType.USAGE_STATS -> {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            settingsLauncher.launch(intent)
                        }
                    }
                },
            ) {
                if (state.overview.allRequiredGranted) {
                    onComplete()
                } else {
                    // 提示用户必须授予必需权限
                }
            }
        }
        
        is OnboardingUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: ${state.message}")
            }
        }
        
        is OnboardingUiState.Completed -> {
            LaunchedEffect(Unit) {
                onComplete()
            }
        }
    }
}

@Composable
private fun OnboardingContent(
    overview: com.hsr.railfocus.domain.model.PermissionsOverview,
    onRequestPermission: (PermissionType) -> Unit,
    onComplete: () -> Unit
) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding() // 自动适配系统导航栏
                .padding(horizontal = 24.dp), // 移除垂直 padding，让内容整体抬高
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(24.dp)) // 缩减顶部间距
                
                Icon(
                    imageVector = Icons.Default.Train,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp), // 稍微缩小图标
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "欢迎使用 Rail Focus",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "专注如同乘坐高铁，一路向前，直达目标",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp)) // 缩减间距
            }
            
            // 权限列表
            item {
                Text(
                    text = "开始前需要以下权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            items(overview.permissions) { permissionState ->
                PermissionCard(
                    permissionState = permissionState,
                    onRequestPermission = { onRequestPermission(permissionState.type) }
                )
            }
            
            // 完成按钮
            item {
                Spacer(modifier = Modifier.height(8.dp)) // 缩减间距
                
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = overview.allRequiredGranted
                ) {
                    Text(
                        text = if (overview.allRequiredGranted) "开始使用" else "请先授予必需权限",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                if (!overview.allImportantGranted && overview.allRequiredGranted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("跳过可选权限")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp)) // 移除原来巨大的 32dp，保留基础间距
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit
) {
    val icon = when (permissionState.type) {
        PermissionType.NOTIFICATIONS -> Icons.Default.Notifications
        PermissionType.SYSTEM_ALERT_WINDOW -> Icons.Default.PictureInPicture
        PermissionType.DO_NOT_DISTURB -> Icons.Default.DoNotDisturb
        PermissionType.USAGE_STATS -> Icons.Default.BarChart
    }
    
    val isGranted = permissionState.status == PermissionStatus.GRANTED
    val isRequired = permissionState.type.priority == PermissionPriority.REQUIRED
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isRequired) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else if (isRequired) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = permissionState.type.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (isRequired) {
                            Text(
                                text = "必需",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = permissionState.type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已授予",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = onRequestPermission,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("授予")
                }
            }
        }
    }
}
