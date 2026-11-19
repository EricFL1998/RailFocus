package com.hsr.railfocus.ui.focus

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.R
import com.hsr.railfocus.service.FocusTimerService
import com.hsr.railfocus.ui.journal.JournalEditDialog
import com.hsr.railfocus.ui.components.*
import org.maplibre.android.geometry.LatLng

/**
 * 专注旅程页面
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
    val context = LocalContext.current
    val view = LocalView.current
    var showStopConfirm by remember { mutableStateOf(false) }
    var showJournalDialog by remember { mutableStateOf(false) }

    val plannedMinutes = (uiState.totalSeconds / 60).coerceAtLeast(1)
    val (trainSeries, prefix) = remember(plannedMinutes, uiState.focusType) {
        com.hsr.railfocus.ui.history.TrainTicketHelper.determineTrainSeries(
            plannedMinutes,
            focusType = uiState.focusType?.displayName
        )
    }
    val trainSeriesName = when (trainSeries) {
        com.hsr.railfocus.ui.history.TrainSeries.G_SERIES -> "复兴号"
        com.hsr.railfocus.ui.history.TrainSeries.D_SERIES -> "和谐号"
        com.hsr.railfocus.ui.history.TrainSeries.C_SERIES -> "城际"
        com.hsr.railfocus.ui.history.TrainSeries.SLEEPER -> "动卧"
    }
    val trainNumber = remember(prefix, uiState.startStation, uiState.endStation, plannedMinutes) {
        com.hsr.railfocus.ui.history.TrainTicketHelper.synthesizeTrainNumber(
            prefix,
            uiState.startStation,
            uiState.endStation,
            plannedMinutes
        )
    }

    // 屏幕常亮：当用户开启设置且旅程处于进行中时保持屏幕常亮
    DisposableEffect(keepScreenOnEnabled, uiState.isRunning) {
        view.keepScreenOn = keepScreenOnEnabled && uiState.isRunning
        onDispose {
            view.keepScreenOn = false
        }
    }

    // 启动前台服务，确保后台运行
    LaunchedEffect(destinationJson) {
        context.startForegroundService(
            FocusTimerService.createStartIntent(context, destinationJson),
        )
    }

    // 使用 PredictiveBackHandler 提供更现代的手势返回体验
    PredictiveBackHandler { progressFlow ->
        try {
            progressFlow.collect { _ -> }
            viewModel.stop()
            onBackHome()
        } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 地图背景
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
            stations = routeStations.drop(1),
            cameraTargetBounds = cameraTargetBounds,
            routeStations = routeStations,
            trainProgress = uiState.overallProgress,
            showTrainMarker = (routeStations.size >= 2),
            enableGestures = false,
            cameraInsetLeftDp = 32,
            cameraInsetTopDp = 300,
            cameraInsetRightDp = 32,
            cameraInsetBottomDp = 180,
        )

        // 2. 前景 UI
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 },
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val haptic = LocalHapticFeedback.current

                // 顶部控制与目的地信息区域
                Box(modifier = Modifier.fillMaxWidth()) {
                    // 左上角：简洁微型白噪音快捷开关
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleAmbientSound()
                        },
                        shape = CircleShape,
                        color = if (ambientEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        },
                        contentColor = if (ambientEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        tonalElevation = 2.dp,
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = if (ambientEnabled) Icons.Default.Headphones else Icons.Default.VolumeOff,
                            contentDescription = stringResource(R.string.settings_ambient_sound),
                            modifier = Modifier.padding(11.dp).size(22.dp)
                        )
                    }

                    // 居中：标题与简洁列车信息（无面板、极简文字）
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.focus_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Text(
                            text = uiState.endStation.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // 简洁列车信息
                        val seatClean = uiState.seatNumber?.replace("号", "")?.replace(Regex("^[0-9]+车"), "")
                        val seatDisplay = if (uiState.carriageNumber != null && seatClean != null) {
                            "${uiState.carriageNumber}车 $seatClean"
                        } else if (uiState.carriageNumber != null) {
                            "${uiState.carriageNumber}车"
                        } else null

                        val trainSubtitle = buildString {
                            append("$trainSeriesName $trainNumber")
                            if (seatDisplay != null) append(" · $seatDisplay")
                            uiState.focusType?.let { append(" · ${it.displayName}") }
                        }
                        Text(
                            text = trainSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // 右上角：暂停与结束小圆形控制按钮
                    FocusTopControls(
                        isPaused = uiState.isPaused,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onStop = { showStopConfirm = true },
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 当前站 / 下一站（支持中途到站停靠面板与巡航面板平滑动画切换）
                StationInfoCard(
                    currentStation = uiState.currentStation,
                    nextStation = uiState.nextStation,
                    speed = uiState.currentSpeed,
                    isDwelling = uiState.isDwelling
                )

                // 底部留白
                Spacer(modifier = Modifier.height(16.dp))
            }
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

@Composable
private fun FocusTopControls(
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "pause_pulse")
    val pausedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "paused_pulse_alpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 暂停 / 继续
        Surface(
            onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                if (isPaused) onResume() else onPause()
            },
            shape = CircleShape,
            color = if (isPaused) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = pausedAlpha)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            },
            contentColor = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(if (isPaused) R.string.focus_resume else R.string.focus_pause),
                modifier = Modifier.padding(12.dp).size(22.dp),
                tint = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        // 结束旅程
        Surface(
            onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onStop()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.focus_stop),
                modifier = Modifier.padding(12.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
