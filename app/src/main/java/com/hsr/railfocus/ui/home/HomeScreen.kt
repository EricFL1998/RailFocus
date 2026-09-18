package com.hsr.railfocus.ui.home

import android.Manifest
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.service.DestinationOption
import com.hsr.railfocus.service.FocusTimerService
import com.hsr.railfocus.ui.components.BulletTrainIcon
import com.hsr.railfocus.ui.components.MapLibreView
import com.hsr.railfocus.ui.components.UpdateAvailableDialog
import com.hsr.railfocus.ui.focus.FocusSessionScreen
import com.hsr.railfocus.ui.focus.FocusSessionViewModel
import com.hsr.railfocus.ui.focus.FocusTypeSelectionPopup
import com.hsr.railfocus.ui.home.components.DataBottomSheet
import com.hsr.railfocus.ui.home.components.LocationHeader
import com.hsr.railfocus.ui.home.components.MyJourneysBottomSheet
import com.hsr.railfocus.ui.theme.RailColors
import com.hsr.railfocus.ui.timeselection.TimeSelectionViewModel
import com.hsr.railfocus.ui.timeselection.components.JourneySelectionContent
import org.maplibre.android.geometry.LatLng

/**
 * 首页
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    viewModel: HomeViewModel = hiltViewModel(),
    focusSessionViewModel: FocusSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusUiState by focusSessionViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var phase by remember { mutableStateOf(HomePhase.None) }
    var pendingDestination by remember { mutableStateOf<DestinationOption?>(null) }
    var showFocusTypePopup by remember { mutableStateOf(false) }

    val timeSelectionViewModel: TimeSelectionViewModel = hiltViewModel()
    val timeSelectionUiState by timeSelectionViewModel.uiState.collectAsState()
    val searchQuery by timeSelectionViewModel.searchQuery.collectAsState()
    val searchResults by timeSelectionViewModel.searchResults.collectAsState()

    // 离开路线选择阶段（进入专注页或返回首页）时，把时长重置回最小值，
    // 保证每次再进入路线选择都默认是最少时间
    LaunchedEffect(phase) {
        when {
            // 进入路线选择时重置；进入专注页不重置，保持选中项与正在进行的旅程一致
            phase == HomePhase.JourneySelection ->
                timeSelectionViewModel.resetToMinimum()
            // 回到首页时重置（包括取消/结束旅程、退出路线选择）。
            // 必须在离开旅程的此刻就重置，而不是等下次进入选择页：
            // 时间选择器首次组合会用旧状态初始化页码并立即回报，覆盖进入时的重置。
            phase == HomePhase.None ->
                timeSelectionViewModel.resetToMinimum()
        }
    }

    val homeTarget = uiState.currentLocation
    val initialZoom = 4.0

    val transitionProgress by animateFloatAsState(
        // 只有路线相关阶段才驱动转场进度；打开"我的/数据"面板时进度保持 0，
        // 避免相机把上次遗留的路线数据当作"进入路线"来框选。
        targetValue = if (phase == HomePhase.JourneySelection || phase == HomePhase.FocusSession) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "phase_transition"
    )

    // 取消/结束旅程后 focusUiState.path 仍残留上次路线数据，
    // 若继续传给相机会在退出路线选择时把旧旅程路线重新框选、绘制到首页。
    // 因此只在专注阶段消费实时路径，退出动画期间用快照，
    // 稳定回到首页后丢弃快照。
    var lastFocusPath by remember { mutableStateOf<List<Station>?>(null) }
    if (phase == HomePhase.FocusSession) {
        focusUiState.path?.path?.let { lastFocusPath = it }
    }
    LaunchedEffect(phase, transitionProgress) {
        if (phase == HomePhase.None && transitionProgress == 0f) {
            lastFocusPath = null
        }
    }

    PredictiveBackHandler(enabled = phase != HomePhase.None) { progressFlow ->
        try {
            progressFlow.collect { _ -> }
            if (phase == HomePhase.FocusSession) {
                focusSessionViewModel.stop()
            }
            phase = HomePhase.None
        } catch (_: Exception) {}
    }

    var bottomPanelHeightWithMargin by remember { androidx.compose.runtime.mutableIntStateOf(180) }
    var bottomPanelMeasured by remember { mutableStateOf(false) }

    SharedTransitionLayout {
        Box(modifier = Modifier.fillMaxSize()) {
            val cameraState = computeCameraState(
                progress = transitionProgress,
                homeTarget = homeTarget,
                startStation = if (phase == HomePhase.JourneySelection) timeSelectionUiState.startStation else uiState.currentStation,
                selectedDestination = timeSelectionUiState.selectedDestination,
                phase = phase,
                focusPath = if (phase == HomePhase.FocusSession) focusUiState.path?.path else lastFocusPath
            )

            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                initialPosition = homeTarget,
                // 路线选择中手动搜索换了起点时，“我的位置”标记临时放到起点站；
                // 返回后传回 null，标记恢复到家基地（首次为真实定位，之后为最近到达的站点）。
                locationMarkerPosition = if (phase == HomePhase.JourneySelection) {
                    timeSelectionUiState.startStation.let { LatLng(it.lat, it.lng) }
                } else null,
                initialZoom = 4.0,
                transitionProgress = transitionProgress,
                stations = emptyList(),
                showStationMarkers = true,
                cameraTargetBounds = cameraState.targetBounds,
                routeStations = cameraState.routeStations,
                trainProgress = focusUiState.overallProgress,
                showTrainMarker = (phase == HomePhase.FocusSession),
                enableGestures = (phase == HomePhase.None || phase == HomePhase.JourneySelection),
                cameraInsetLeftDp = 32,
                cameraInsetTopDp = if (phase == HomePhase.FocusSession) 300 else 100,
                cameraInsetRightDp = 32,
                cameraInsetBottomDp = if (phase == HomePhase.FocusSession) 180 else bottomPanelHeightWithMargin,
            )

            val isPanelActive = phase == HomePhase.MyJourneys || phase == HomePhase.Data
            if (phase == HomePhase.None || isPanelActive) {
                HomeState(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = if (phase == HomePhase.None) animatedVisibilityScope else null,
                    uiState = uiState,
                    onStartJourney = { phase = HomePhase.JourneySelection },
                    onMyJourneysClick = { phase = HomePhase.MyJourneys },
                    onDataClick = { phase = HomePhase.Data },
                    onSettingsClick = {
                        phase = HomePhase.None
                        onSettingsClick()
                    },
                )
            }

            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(
                    tween(300)) },
                label = "home_phase_content"
            ) { targetPhase ->
                when (targetPhase) {
                    HomePhase.JourneySelection -> {
                        RouteSelectionState(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this@AnimatedContent,
                            timeSelectionViewModel = timeSelectionViewModel,
                            timeSelectionUiState = timeSelectionUiState,
                            searchQuery = searchQuery,
                            searchResults = searchResults,
                            onDismiss = { phase = HomePhase.None },
                            onStartJourney = { destination: DestinationOption ->
                                pendingDestination = destination
                                showFocusTypePopup = true
                            },
                            onBottomPanelHeightChanged = { height: Int ->
                                bottomPanelHeightWithMargin = height
                                bottomPanelMeasured = true
                            },
                        )
                    }
                    HomePhase.FocusSession -> {
                        FocusSessionScreen(
                            onBackHome = {
                                phase = HomePhase.None
                                focusSessionViewModel.stop()
                            },
                            destinationJson = pendingDestination?.toJson()
                                ?: focusUiState.restoredDestinationJson,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this@AnimatedContent,
                        )
                    }
                    else -> Box(Modifier.fillMaxSize())
                }
            }

            LaunchedEffect(phase) {
                // 只要是从 None 切换到 JourneySelection，就触发更新。
                // 内部的 updateStartStation 会处理缓存逻辑。
                if (phase == HomePhase.JourneySelection) {
                    timeSelectionViewModel.updateStartStation(uiState.currentStation)
                }
            }

            // 启动后预计算当前车站默认时长的目的地（后台进行，不显示加载），
            // 首次进入路线选择时结果已就绪，避免冷启动后的首次加载等待。
            // updateStartStation 对同站且有结果的情况会直接返回，重复触发无副作用。
            LaunchedEffect(uiState.currentStation) {
                if (phase == HomePhase.None) {
                    timeSelectionViewModel.updateStartStation(uiState.currentStation)
                }
            }

            // 进程被杀后恢复：数据库中存在 ACTIVE 旅程时自动回到专注页
            LaunchedEffect(focusUiState.isRestored) {
                if (focusUiState.isRestored && phase == HomePhase.None) {
                    phase = HomePhase.FocusSession
                }
            }

            if (phase == HomePhase.MyJourneys) {
                MyJourneysBottomSheet(
                    onDismiss = { phase = HomePhase.None },
                    onSettingsClick = {
                        phase = HomePhase.None
                        onSettingsClick()
                    }
                )
            }
            if (phase == HomePhase.Data) {
                DataBottomSheet(onDismiss = { phase = HomePhase.None })
            }

            val focusTypes by viewModel.focusTypes.collectAsState()

            if (showFocusTypePopup && pendingDestination != null) {
                FocusTypeSelectionPopup(
                    focusTypes = focusTypes,
                    onFocusTypeSelected = { focusType, seatNumber ->
                        pendingDestination?.let { destination ->
                            try {
                                context.startForegroundService(
                                    FocusTimerService.createStartIntent(context, destination.toJson())
                                )
                            } catch (_: Exception) {}

                            phase = HomePhase.FocusSession
                            focusSessionViewModel.startJourney(
                                destination = destination,
                                focusType = focusType,
                                seatNumber = seatNumber
                            )
                        }
                        showFocusTypePopup = false
                        pendingDestination = null
                    },
                    onDismiss = {
                        showFocusTypePopup = false
                        pendingDestination = null
                    }
                )
            }

            // 启动时自动检查更新：发现新版本弹出提示，忽略同一版本后不再打扰
            val pendingUpdate by viewModel.pendingUpdate.collectAsState()
            var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
            pendingUpdate?.let { updateInfo ->
                if (dismissedUpdateVersion != updateInfo.version) {
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
    }
}

private enum class HomePhase {
    None, MyJourneys, Data, JourneySelection, FocusSession
}

private data class CameraState(
    val position: LatLng,
    val zoom: Double,
    val targetBounds: List<LatLng> = emptyList(),
    val routeStations: List<Station> = emptyList()
)

@Composable
private fun computeCameraState(
    progress: Float,
    homeTarget: LatLng,
    startStation: Station,
    selectedDestination: DestinationOption?,
    phase: HomePhase,
    focusPath: List<Station>?
): CameraState {
    val isRoutePerspective = phase == HomePhase.JourneySelection || phase == HomePhase.FocusSession
    val isExiting = !isRoutePerspective && progress > 0f

    // 关键修正：即便 phase 已经切换回 None，但在退出动画 (isExiting) 期间，
    // 我们必须继续提供之前的路线数据，否则地图层会瞬间变空导致“消失”感。
    val currentRoute = if (phase == HomePhase.FocusSession) {
        focusPath
    } else if (phase == HomePhase.JourneySelection) {
        selectedDestination?.pathStations
    } else if (isExiting) {
        // 退出阶段：保留之前的路线供地图参考（用于退出动画）
        focusPath ?: selectedDestination?.pathStations
    } else null

    if (currentRoute.isNullOrEmpty()) {
        return CameraState(homeTarget, 4.0)
    }

    val bounds = currentRoute.map { LatLng(it.lat, it.lng) }
    
    // 注意：始终以 homeTarget 为基准位置，地图组件内部会根据 bounds 自动切换视角
    return CameraState(homeTarget, 4.0, bounds, currentRoute)
}

@Composable
private fun HomeState(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    uiState: HomeUiState,
    onStartJourney: () -> Unit,
    onMyJourneysClick: () -> Unit,
    onDataClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        with(sharedTransitionScope) {
            LocationHeader(
                greeting = when(uiState.greeting) {
                    "home_greeting_morning" -> stringResource(R.string.home_greeting_morning)
                    "home_greeting_afternoon" -> stringResource(R.string.home_greeting_afternoon)
                    "home_greeting_evening" -> stringResource(R.string.home_greeting_evening)
                    else -> stringResource(R.string.home_greeting_default)
                },
                cityName = if (uiState.currentStationName == "南京") stringResource(R.string.home_city_placeholder) else uiState.currentStationName,
                stationName = if (uiState.currentStationDisplayName == "南京南站") stringResource(R.string.home_location_placeholder) else uiState.currentStationDisplayName,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .then(
                        if (animatedVisibilityScope != null) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "location-header"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        } else Modifier
                    )
            )
        }

        HomeMenuContent(
            onStartJourney = onStartJourney,
            onMyJourneysClick = onMyJourneysClick,
            onDataClick = onDataClick,
            onSettingsClick = onSettingsClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

@Composable
private fun HomeMenuContent(
    onStartJourney: () -> Unit,
    onMyJourneysClick: () -> Unit,
    onDataClick: () -> Unit,
    onSettingsClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            with(sharedTransitionScope) {
                Button(
                    onClick = onStartJourney,
                    modifier = if (animatedVisibilityScope != null) {
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .sharedBounds(
                                rememberSharedContentState(key = "start-journey-button"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                boundsTransform = { _, _ -> spring(stiffness = 300f, dampingRatio = 0.8f) }
                            )
                    } else {
                        Modifier.fillMaxWidth().height(64.dp)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    BulletTrainIcon(
                        contentDescription = null,
                        size = 28.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.home_start_journey),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            with(sharedTransitionScope) {
                Surface(
                    modifier = if (animatedVisibilityScope != null) {
                        Modifier
                            .fillMaxWidth()
                            .sharedBounds(
                                rememberSharedContentState(key = "menu-panel"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                boundsTransform = { _, _ -> spring(stiffness = 300f, dampingRatio = 0.8f) }
                            )
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MenuRow(
                            title = stringResource(R.string.home_my_journeys),
                            leadingIcon = {
                                BulletTrainIcon(
                                    contentDescription = null,
                                    size = 28.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onMyJourneysClick,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        MenuRow(
                            title = stringResource(R.string.home_data),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = onDataClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        leadingIcon()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun RouteSelectionState(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    timeSelectionViewModel: TimeSelectionViewModel,
    timeSelectionUiState: com.hsr.railfocus.ui.timeselection.TimeSelectionUiState,
    searchQuery: String,
    searchResults: List<Station>,
    onDismiss: () -> Unit,
    onStartJourney: (DestinationOption) -> Unit,
    onBottomPanelHeightChanged: (Int) -> Unit,
) {
    JourneySelectionContent(
        uiState = timeSelectionUiState,
        searchQuery = searchQuery,
        searchResults = searchResults,
        onBack = onDismiss,
        onStartFocus = onStartJourney,
        onShowStationSearch = { },
        onSearchQueryChanged = timeSelectionViewModel::onSearchQueryChanged,
        onStationSelected = { station -> timeSelectionViewModel.updateStartStation(station) },
        onDurationSelected = timeSelectionViewModel::onDurationSelected,
        onDestinationSelected = timeSelectionViewModel::onDestinationSelected,
        showMap = false,
        showBackArrow = false,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBottomPanelHeightChanged = onBottomPanelHeightChanged,
    )
}
