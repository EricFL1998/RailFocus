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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.PermissionPriority
import com.hsr.railfocus.domain.model.PermissionState
import com.hsr.railfocus.domain.model.PermissionStatus
import com.hsr.railfocus.domain.model.PermissionsOverview
import com.hsr.railfocus.domain.model.PermissionType
import kotlinx.coroutines.launch

/** 功能亮点页数据 */
private data class FeaturePage(
    val icon: ImageVector,
    val title: Int,
    val subtitle: Int,
    val description: Int,
)

private val featurePages = listOf(
    FeaturePage(
        icon = Icons.Default.Route,
        title = R.string.onboarding_feature_journey_title,
        subtitle = R.string.onboarding_feature_journey_subtitle,
        description = R.string.onboarding_feature_journey_desc,
    ),
    FeaturePage(
        icon = Icons.Default.AutoAwesome,
        title = R.string.onboarding_feature_journal_title,
        subtitle = R.string.onboarding_feature_journal_subtitle,
        description = R.string.onboarding_feature_journal_desc,
    ),
    FeaturePage(
        icon = Icons.Default.DateRange,
        title = R.string.onboarding_feature_recall_title,
        subtitle = R.string.onboarding_feature_recall_subtitle,
        description = R.string.onboarding_feature_recall_desc,
    ),
    FeaturePage(
        icon = Icons.Default.EmojiEvents,
        title = R.string.onboarding_feature_club_title,
        subtitle = R.string.onboarding_feature_club_subtitle,
        description = R.string.onboarding_feature_club_desc,
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

    // 位置权限需要同时请求精确与粗略定位
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
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
                        PermissionType.LOCATION -> {
                            locationLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
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
                            contentDescription = stringResource(R.string.onboarding_prev_page),
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
                            isLastPage && overview.allRequiredGranted -> stringResource(R.string.onboarding_start)
                            isLastPage -> stringResource(R.string.onboarding_grant_required)
                            else -> stringResource(R.string.onboarding_continue)
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
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_slogan),
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
            text = stringResource(page.title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(page.subtitle),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(page.description),
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
                text = stringResource(R.string.onboarding_perm_title),
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
                    text = stringResource(R.string.onboarding_perm_optional_hint),
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
        PermissionType.LOCATION -> Icons.Default.LocationOn
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
                            text = stringResource(permissionState.type.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (isRequired) {
                            Text(
                                text = stringResource(R.string.perm_state_required),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(permissionState.type.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.perm_state_granted),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = onRequestPermission,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.perm_state_grant))
                }
            }
        }
    }
}
