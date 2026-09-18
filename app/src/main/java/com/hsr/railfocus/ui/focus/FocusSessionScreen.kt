package com.hsr.railfocus.ui.focus

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.R
import com.hsr.railfocus.service.FocusTimerService
import com.hsr.railfocus.ui.components.*
import org.maplibre.android.geometry.LatLng

/**
 * 专注旅程页面
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FocusSessionScreen(
    onBackHome: () -> Unit,
    destinationJson: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: FocusSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                // 顶部标题 + 右上角小圆形控制按钮
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.focus_estimated_time, uiState.totalSeconds / 60),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            uiState.seatNumber?.let { seat ->
                                Text(
                                    text = " · " + stringResource(R.string.focus_seat, seat),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    FocusTopControls(
                        isPaused = uiState.isPaused,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onStop = {
                            viewModel.stop()
                            onBackHome()
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 倒计时
                CountdownDisplay(remainingSeconds = uiState.remainingSeconds)

                Spacer(modifier = Modifier.heightIn(min = 24.dp).weight(1f))

                // 当前站 / 下一站
                StationInfoCard(
                    currentStation = uiState.currentStation,
                    nextStation = uiState.nextStation,
                    speed = uiState.currentSpeed
                )

                // 底部不留大按钮，控制已移到右上角
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 暂停 / 继续
        Surface(
            onClick = if (isPaused) onResume else onPause,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(if (isPaused) R.string.focus_resume else R.string.focus_pause),
                modifier = Modifier.padding(12.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // 结束旅程
        Surface(
            onClick = onStop,
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
