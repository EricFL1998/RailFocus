package com.hsr.railfocus.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.R
import com.hsr.railfocus.data.repository.UpdateCheckResult
import com.hsr.railfocus.ui.components.UpdateAvailableDialog

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFocusTypeSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val ambientEnabled by viewModel.ambientSoundEnabled.collectAsState()
    val stationAnnouncementEnabled by viewModel.stationAnnouncementEnabled.collectAsState()
    val keepScreenOnEnabled by viewModel.keepScreenOnEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
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

    var permissionCheckKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val locationGranted = remember(permissionCheckKey) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val notificationGranted = if (Build.VERSION.SDK_INT >= 33) {
        remember(permissionCheckKey) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
    } else {
        true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionCheckKey++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            if (!locationGranted || !notificationGranted) {
                PermissionWarningSection(
                    locationGranted = locationGranted,
                    notificationGranted = notificationGranted,
                    onRequestPermissions = {
                        val permissions = mutableListOf<String>()
                        if (!locationGranted) {
                            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                        if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        
                        if (permissions.isNotEmpty()) {
                            permissionLauncher.launch(permissions.toTypedArray())
                        } else if (!notificationGranted) {
                            openAppSettings(context)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 场景管理：独立、突出
            FocusManagementSection(
                onNavigate = onNavigateToFocusTypeSettings,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            ThemeSection(
                currentMode = uiState.themeMode,
                onModeSelected = viewModel::setThemeMode,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SoundToggleSection(
                icon = Icons.Default.VolumeUp,
                titleRes = R.string.settings_ambient_sound,
                summaryRes = R.string.settings_ambient_sound_summary,
                enabled = ambientEnabled,
                onToggle = viewModel::setAmbientSoundEnabled,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SoundToggleSection(
                icon = Icons.Default.Notifications,
                titleRes = R.string.settings_station_announcement,
                summaryRes = R.string.settings_station_announcement_summary,
                enabled = stationAnnouncementEnabled,
                onToggle = viewModel::setStationAnnouncementEnabled,
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
private fun PermissionWarningSection(
    locationGranted: Boolean,
    notificationGranted: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(20.dp),
        onClick = onRequestPermissions
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GppMaybe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_permissions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                val missing = buildList {
                    if (!locationGranted) add("位置")
                    if (!notificationGranted) add("通知")
                }.joinToString("、")
                Text(
                    text = stringResource(R.string.settings_permissions) + " " + missing,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun FocusManagementSection(
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigate),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_focus_management),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "自定义您的专注场景和图标",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
            )
        }
    }
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
private fun SoundToggleSection(
    icon: ImageVector,
    @androidx.annotation.StringRes titleRes: Int,
    @androidx.annotation.StringRes summaryRes: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
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
                    text = "外观",
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

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
