package com.hsr.railfocus.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.PermissionType
import kotlinx.coroutines.launch
import com.hsr.railfocus.data.repository.UpdateCheckResult
import com.hsr.railfocus.ui.components.UpdateAvailableDialog

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFocusTypeSettings: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val ambientEnabled by viewModel.ambientSoundEnabled.collectAsState()
    val ambientVolume by viewModel.ambientSoundVolume.collectAsState()
    val stationAnnouncementEnabled by viewModel.stationAnnouncementEnabled.collectAsState()
    val keepScreenOnEnabled by viewModel.keepScreenOnEnabled.collectAsState()
    val weatherDisplayEnabled by viewModel.weatherDisplayEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // 供启动器回调等非 Composable 上下文使用的文案模板，需在 composable 中提前求值
    val exportFailedTemplate = stringResource(R.string.settings_export_failed)
    val importFailedTemplate = stringResource(R.string.settings_import_failed_with_reason)
    var showClearDataDialog by remember { mutableStateOf(false) }
    val updateCheck by viewModel.updateCheckState.collectAsState()
    val pendingUpdate by viewModel.pendingUpdate.collectAsState()
    val scope = rememberCoroutineScope()

    val appVersion = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.let { stream ->
                    viewModel.exportData(stream, appVersion) { success, msg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(exportFailedTemplate.format(e.message))
                }
            }
        }
    }

    // 导入备份：先确认，再读取文件
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    // 打开设置页时自动静默检查一次更新；有新版本时弹窗提示
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate(appVersion, silent = true)
    }

    // 手动检查的结果通过 snackbar 反馈（有新版本时走弹窗，不在这里提示）
    val msgUpToDate = stringResource(R.string.settings_update_up_to_date)
    val msgNoRelease = stringResource(R.string.settings_update_no_release)
    val msgCheckFailed = stringResource(R.string.settings_update_failed)
    LaunchedEffect(updateCheck) {
        val result = (updateCheck as? UpdateCheckState.Done)?.result ?: return@LaunchedEffect
        val message = when (result) {
            is UpdateCheckResult.UpToDate -> msgUpToDate
            is UpdateCheckResult.NoRelease -> msgNoRelease
            is UpdateCheckResult.Failure -> msgCheckFailed
            is UpdateCheckResult.UpdateAvailable -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.resetUpdateCheck()
        }
    }

    // 进入设置页时检查一次全部权限；可选权限未开启时逐项提醒
    val permissionStates by viewModel.permissionStates.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    // 普通运行时权限（定位/通知/录音）请求结果
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
    }

    // 特殊权限（悬浮窗/勿扰/使用统计）从系统设置页返回后刷新
    val specialPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshPermissions()
    }

    val onRequestPermission: (PermissionType) -> Unit = { type ->
        when (type) {
            PermissionType.LOCATION -> permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
            PermissionType.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            PermissionType.RECORD_AUDIO -> {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
            PermissionType.SYSTEM_ALERT_WINDOW -> specialPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
            )
            PermissionType.DO_NOT_DISTURB -> specialPermissionLauncher.launch(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            )
            PermissionType.USAGE_STATS -> specialPermissionLauncher.launch(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.selection_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 未授予的权限（含可选权限）逐项提醒
            val missingPermissions = permissionStates.filter {
                it.status != com.hsr.railfocus.domain.model.PermissionStatus.GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                PermissionsReminderSection(
                    missingPermissions = missingPermissions,
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            ThemeSection(
                currentMode = uiState.themeMode,
                onModeSelected = viewModel::setThemeMode,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 声音设置：车厢环境音与到站发车提示音合为一个面板
            CombinedSoundSection(
                ambientEnabled = ambientEnabled,
                onAmbientToggle = viewModel::setAmbientSoundEnabled,
                ambientVolume = ambientVolume,
                onVolumeChange = viewModel::setAmbientSoundVolume,
                announcementEnabled = stationAnnouncementEnabled,
                onAnnouncementToggle = viewModel::setStationAnnouncementEnabled,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SoundToggleSection(
                icon = Icons.Default.BrightnessHigh,
                titleRes = R.string.settings_keep_screen_on,
                summaryRes = R.string.settings_keep_screen_on_summary,
                enabled = keepScreenOnEnabled,
                onToggle = viewModel::setKeepScreenOnEnabled,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SoundToggleSection(
                icon = Icons.Default.WbSunny,
                titleRes = R.string.settings_weather_display,
                summaryRes = R.string.settings_weather_display_summary,
                enabled = weatherDisplayEnabled,
                onToggle = viewModel::setWeatherDisplayEnabled,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SettingsSection(
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                val checkingUpdate = updateCheck is UpdateCheckState.Checking
                val updateAvailable = pendingUpdate != null
                SettingsClickableItem(
                    icon = Icons.Default.SystemUpdateAlt,
                    title = stringResource(R.string.settings_check_update),
                    summary = when {
                        checkingUpdate -> stringResource(R.string.settings_checking_update)
                        updateAvailable -> stringResource(
                            R.string.settings_update_found,
                            pendingUpdate!!.version,
                        )
                        else -> stringResource(R.string.settings_current_version, appVersion)
                    },
                    summaryColor = if (updateAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { viewModel.checkForUpdate(appVersion) },
                )
                SettingsClickableItem(
                    icon = Icons.Default.Policy,
                    title = stringResource(R.string.settings_privacy),
                    summary = stringResource(R.string.settings_privacy),
                    onClick = { /* TODO */ },
                )
            }

            // 数据备份：导出全部数据
            SettingsSection(
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                SettingsClickableItem(
                    icon = Icons.Default.FileDownload,
                    title = stringResource(R.string.settings_export_data),
                    summary = stringResource(R.string.settings_export_data_summary),
                    onClick = {
                        val timeStr = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                        createDocLauncher.launch("rail_focus_backup_$timeStr.zip")
                    },
                )
                SettingsClickableItem(
                    icon = Icons.Default.FileUpload,
                    title = stringResource(R.string.settings_import_data),
                    summary = stringResource(R.string.settings_import_data_summary),
                    onClick = {
                        openDocLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*"))
                    },
                )
            }

            // 清除数据：警告色、独立
            ClearDataSection(
                onClearClick = { showClearDataDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text(stringResource(R.string.settings_clear_data)) },
                text = { Text(stringResource(R.string.settings_clear_data_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllData()
                            showClearDataDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.settings_confirm_clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            )
        }

        pendingImportUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingImportUri = null },
                title = { Text(stringResource(R.string.settings_import_data)) },
                text = { Text(stringResource(R.string.settings_import_data_summary)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingImportUri = null
                            try {
                                context.contentResolver.openInputStream(uri)?.let { stream ->
                                    viewModel.importData(stream) { success, msg ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(importFailedTemplate.format(e.message))
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_import))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImportUri = null }) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            )
        }

        val updateInfo =
            ((updateCheck as? UpdateCheckState.Done)?.result as? UpdateCheckResult.UpdateAvailable)?.info
        var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
        if (updateInfo != null && dismissedUpdateVersion != updateInfo.version) {
            UpdateAvailableDialog(
                info = updateInfo,
                onDismissed = {
                    dismissedUpdateVersion = updateInfo.version
                    viewModel.dismissPendingUpdate()
                },
            )
        }
    }
}

@Composable
private fun PermissionsReminderSection(
    missingPermissions: List<com.hsr.railfocus.domain.model.PermissionState>,
    onRequestPermission: (PermissionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GppMaybe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = stringResource(R.string.settings_permissions_missing_hint),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            // 每个未开启的权限单独一行，点按即发起对应的开启流程
            missingPermissions.forEach { state ->
                PermissionReminderRow(
                    state = state,
                    onClick = { onRequestPermission(state.type) },
                )
            }
        }
    }
}

@Composable
private fun PermissionReminderRow(
    state: com.hsr.railfocus.domain.model.PermissionState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = permissionIcon(state.type.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(state.type.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(state.type.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
        }
        Text(
            text = stringResource(R.string.perm_state_grant),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** PermissionType.icon 字符串名到 Material 图标的映射 */
private fun permissionIcon(name: String): ImageVector = when (name) {
    "notifications" -> Icons.Default.Notifications
    "picture_in_picture" -> Icons.Default.PictureInPicture
    "do_not_disturb" -> Icons.Default.DoNotDisturb
    "bar_chart" -> Icons.Default.BarChart
    "record_voice_over" -> Icons.Default.RecordVoiceOver
    "location_on" -> Icons.Default.LocationOn
    else -> Icons.Default.GppMaybe
}

@Composable
private fun ClearDataSection(
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClearClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.settings_clear_data),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CombinedSoundSection(
    ambientEnabled: Boolean,
    onAmbientToggle: (Boolean) -> Unit,
    ambientVolume: Int,
    onVolumeChange: (Int) -> Unit,
    announcementEnabled: Boolean,
    onAnnouncementToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 车厢环境音
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_ambient_sound),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_ambient_sound_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = ambientEnabled,
                    onCheckedChange = onAmbientToggle,
                )
            }

            // 环境音量滑块
            AnimatedVisibility(
                visible = ambientEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ambient_volume),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${ambientVolume}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = ambientVolume.toFloat(),
                        onValueChange = { onVolumeChange(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 两个声音设置项之间的分割线
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // 2. 到站发车提示音
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_station_announcement),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_station_announcement_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = announcementEnabled,
                    onCheckedChange = onAnnouncementToggle,
                )
            }
        }
    }
}

@Composable
private fun SoundToggleSection(
    icon: ImageVector,
    @androidx.annotation.StringRes titleRes: Int,
    @androidx.annotation.StringRes summaryRes: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    volume: Int? = null,
    onVolumeChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(summaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                )
            }

            if (volume != null && onVolumeChange != null) {
                AnimatedVisibility(
                    visible = enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_ambient_volume),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${volume}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = volume.toFloat(),
                            onValueChange = { onVolumeChange(it.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Brush,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_section_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val modes = listOf(
                "auto" to stringResource(R.string.settings_theme_auto),
                "light" to stringResource(R.string.settings_theme_light),
                "dark" to stringResource(R.string.settings_theme_dark),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = currentMode == mode,
                        onClick = { onModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
                        ),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    summary: String,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = summaryColor
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}
