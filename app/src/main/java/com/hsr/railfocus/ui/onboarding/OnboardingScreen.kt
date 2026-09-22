package com.hsr.railfocus.ui.onboarding

import androidx.core.net.toUri

import android.content.Intent

import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.domain.model.PermissionPriority
import com.hsr.railfocus.domain.model.PermissionState
import com.hsr.railfocus.domain.model.PermissionStatus
import com.hsr.railfocus.domain.model.PermissionsOverview
import com.hsr.railfocus.domain.model.PermissionType
import kotlinx.coroutines.launch

/** 功能亮点页数据 */
private data class FeaturePage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val description: String,
)

private val featurePages = listOf(
    FeaturePage(
        icon = Icons.Default.Route,
        title = "沉浸专注旅程",
        subtitle = "选一座出发站，再选一座终点",
        description = "把每一次专注当作一段车程。计时、环境音、到站播报全程伴随，锁屏或切后台也不会中断，准点抵达后自动封存旅程。",
    ),
    FeaturePage(
        icon = Icons.Default.AutoAwesome,
        title = "旅行手账",
        subtitle = "封存此刻的心情",
        description = "旅程结束时，写下一句话，配上两三张照片，或录一段 60 秒语音。当次封存，不留空白，文字、照片与语音一起收进时间胶囊。",
    ),
    FeaturePage(
        icon = Icons.Default.DateRange,
        title = "那年今日",
        subtitle = "故地重游，旧账自开",
        description = "每年的同一天再次经过，当年的手账会静静唤醒。未来三天内是重逢的期许，错过的日子里也有熟悉的问候。",
    ),
    FeaturePage(
        icon = Icons.Default.EmojiEvents,
        title = "常客俱乐部",
        subtitle = "专注里程，一路升级",
        description = "连续打卡与累计里程会提升你的会员等级。全部数据随时可以导出备份，专注成果永不丢失。",
    ),
)

@Composable
fun OnboardingScreen(
    startAtPermissions: Boolean,
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
                startAtPermissions = startAtPermissions,
                onRequestPermission = { type ->
                    when (type) {
                        PermissionType.RECORD_AUDIO -> {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
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
                onComplete = { viewModel.completeOnboarding() },
            )
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
    overview: PermissionsOverview,
    startAtPermissions: Boolean,
    onRequestPermission: (PermissionType) -> Unit,
    onComplete: () -> Unit,
) {
    // 第 0 页为欢迎页，之后为功能页，最后一页为权限页
    val pageCount = featurePages.size + 2
    val pagerState = rememberPagerState(
        initialPage = if (startAtPermissions) pageCount - 1 else 0,
        pageCount = { pageCount },
    )
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            // 顶部：仅在非首页显示返回上一页
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    IconButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "上一页",
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when {
                    page == 0 -> WelcomePage()
                    page == pageCount - 1 -> PermissionsPage(
                        overview = overview,
                        onRequestPermission = onRequestPermission,
                    )
                    else -> FeaturePageContent(featurePages[page - 1])
                }
            }

            // 底部：指示点 + 前进按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageIndicator(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                )

                val isLastPage = pagerState.currentPage == pageCount - 1

                Button(
                    onClick = {
                        if (isLastPage) {
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = !isLastPage || overview.allRequiredGranted,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = when {
                            isLastPage && overview.allRequiredGranted -> "开始使用"
                            isLastPage -> "请先授予必需权限"
                            else -> "继续"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!isLastPage) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Train,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "欢迎使用 Rail Focus",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "专注如同乘坐高铁\n一路向前，直达目标",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeaturePageContent(page: FeaturePage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = page.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionsPage(
    overview: PermissionsOverview,
    onRequestPermission: (PermissionType) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "开始前需要以下权限",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        items(overview.permissions) { permissionState ->
            PermissionCard(
                permissionState = permissionState,
                onRequestPermission = { onRequestPermission(permissionState.type) },
            )
        }

        item {
            if (!overview.allImportantGranted && overview.allRequiredGranted) {
                Text(
                    text = "可选权限可随时在系统设置中补充",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (isSelected) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit
) {
    val icon = when (permissionState.type) {
        PermissionType.RECORD_AUDIO -> Icons.Default.RecordVoiceOver
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
