package com.hsr.railfocus.ui.focus

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.R
import com.hsr.railfocus.service.FocusTimerService
import com.hsr.railfocus.ui.components.CompletionOverlay
import com.hsr.railfocus.ui.components.MapLibreView
import com.hsr.railfocus.ui.journal.JournalEditDialog
import org.maplibre.android.geometry.LatLng

/**
 * 专注旅程页面（去噪版）
 *
 * 单焦点设计：中心只有地图 + 列车，底部一个 72sp 主数字 + 一条进度线。
 * 车次号/座位号移到打卡页，顶部三行标题改为一行语境。
 * 双击进入纯净模式，30 秒无操作边缘控件自动淡出。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun FocusSessionScreen(
    onBackHome: () -> Unit,
    destinationJson: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: FocusSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val keepScreenOnEnabled by viewModel.keepScreenOnEnabled.collectAsState()
    val ambientEnabled by viewModel.ambientSoundEnabled.collectAsState()
    val focusDensity by viewModel.focusDensity.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    var showStopConfirm by remember { mutableStateOf(false) }
    var showJournalDialog by remember { mutableStateOf(false) }
    var pureModeOverride by remember { mutableStateOf<Boolean?>(null) }
    val isPureMode = pureModeOverride ?: (focusDensity == "pure")

    // 屏幕常亮
    DisposableEffect(keepScreenOnEnabled, uiState.isRunning) {
        view.keepScreenOn = keepScreenOnEnabled && uiState.isRunning
        onDispose { view.keepScreenOn = false }
    }

    // 启动前台服务
    LaunchedEffect(destinationJson) {
        context.startForegroundService(
            FocusTimerService.createStartIntent(context, destinationJson),
        )
    }

    // 预测性返回
    PredictiveBackHandler { progressFlow ->
        try {
            progressFlow.collect { _ -> }
            viewModel.stop()
            onBackHome()
        } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 地图背景（去噪：只留关键站点，隐藏定位点，地名降透明度）
        val routeStations = uiState.path?.path ?: emptyList()
        val startLatLng = LatLng(uiState.startStation.lat, uiState.startStation.lng)
        val routePoints = routeStations.map { LatLng(it.lat, it.lng) }
        val cameraTargetBounds = buildList {
            add(startLatLng)
            addAll(routePoints)
        }

        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            initialPosition = startLatLng,
            initialZoom = 4.0,
            transitionProgress = 1f,
            stations = emptyList(),
            showStationMarkers = false,
            cameraTargetBounds = cameraTargetBounds,
            routeStations = routeStations,
            trainProgress = uiState.overallProgress,
            showTrainMarker = (routeStations.size >= 2),
            enableGestures = false,
            cameraInsetLeftDp = 32,
            cameraInsetTopDp = 120,
            cameraInsetRightDp = 32,
            cameraInsetBottomDp = 220,
            showLocationMarker = false,
            basemapLabelOpacity = 0.45f,
        )

        // 2. 边缘控件（44dp 玻璃圆，30s 自动淡出，双击纯净）
        FocusEdgeControls(
            isPaused = uiState.isPaused,
            isPureMode = isPureMode,
            onPauseToggle = { if (uiState.isPaused) viewModel.resume() else viewModel.pause() },
            onStop = { showStopConfirm = true },
            onToggleAmbient = viewModel::toggleAmbientSound,
            ambientEnabled = ambientEnabled,
            onDoubleTap = { pureModeOverride = !isPureMode },
        )

        // 3. 纯净模式只保留剩余时间 + 进度线
        if (isPureMode) {
            com.hsr.railfocus.ui.focus.PureFocusHud(
                remainingSeconds = uiState.remainingSeconds,
                progress = uiState.overallProgress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            )
        }

        // 全量档补充可折叠的站序信息，不影响默认与纯净档
        if (focusDensity == "full" && !isPureMode && uiState.path != null) {
            com.hsr.railfocus.ui.components.RouteProgressionStrip(
                stations = routeStations,
                currentStation = uiState.currentStation,
                nextStation = uiState.nextStation,
                overallProgress = uiState.overallProgress,
                isDwelling = uiState.isDwelling,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 76.dp, start = 24.dp, end = 24.dp),
            )
        }

        // 4. 标准 HUD（语境行 + 进度线 + 主数字 + 副信息）
        AnimatedVisibility(
            visible = !isPureMode,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FocusHud(
                state = uiState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            )
        }

        // 完成覆盖层
        if (uiState.isCompleted) {
            CompletionOverlay(
                startStation = uiState.startStation.name,
                endStation = uiState.endStation.name,
                city = uiState.endStation.city,
                duration = uiState.totalSeconds / 60,
                focusType = uiState.focusType,
                stationFact = uiState.stationFact,
                delayMinutes = uiState.delayMinutes,
                journal = uiState.journal,
                memoryRecall = uiState.memoryRecall,
                onWriteJournal = { showJournalDialog = true },
                onBackHome = onBackHome
            )
        }

        if (showJournalDialog) {
            val initialImages = uiState.journal?.imagePaths?.map { android.net.Uri.fromFile(java.io.File(it)) } ?: emptyList()
            JournalEditDialog(
                startStation = uiState.startStation.name,
                endStation = uiState.endStation.name,
                seatNumber = uiState.seatNumber,
                carriageNumber = uiState.carriageNumber,
                initialContent = uiState.journal?.content ?: "",
                initialImages = initialImages,
                initialAudioPath = uiState.journal?.audioPath,
                initialAudioDurationSec = uiState.journal?.audioDurationSec ?: 0,
                onDismiss = { showJournalDialog = false },
                onSave = { text, images, audioPath, audioDurationSec ->
                    viewModel.saveJournal(text, images, audioPath, audioDurationSec) {
                        showJournalDialog = false
                    }
                }
            )
        }

        // 取消后的"未完成"车票
        if (uiState.isStopped && !uiState.isCompleted) {
            CompletionOverlay(
                startStation = uiState.startStation.name,
                endStation = uiState.endStation.name,
                city = uiState.endStation.city,
                duration = ((uiState.totalSeconds - uiState.remainingSeconds) / 60).coerceAtLeast(1),
                focusType = uiState.focusType,
                stationFact = null,
                completed = false,
                delayMinutes = uiState.delayMinutes,
                onBackHome = onBackHome
            )
        }

        // 错误提示
        uiState.error?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text(stringResource(R.string.focus_ok))
                    }
                },
                title = { Text(stringResource(R.string.focus_error_title)) },
                text = { Text(error) }
            )
        }

        // 取消旅程确认弹窗
        if (showStopConfirm) {
            AlertDialog(
                onDismissRequest = { showStopConfirm = false },
                title = { Text(stringResource(R.string.focus_stop_confirm_title)) },
                text = { Text(stringResource(R.string.focus_stop_confirm_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showStopConfirm = false
                            viewModel.stop()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.focus_stop_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirm = false }) {
                        Text(stringResource(R.string.focus_stop_keep))
                    }
                },
            )
        }
    }
}
