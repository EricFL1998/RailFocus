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
    onAllJourneysClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
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

    // 进入路线选择时把时长重置回最小值，保证每次进入都默认是最少时间。
    // 返回首页时的重置挪到退出转场结束之后（见下面回到首页稳定后的重置）：
    // 在离开的那一刻重置会把目的地列表整表换掉，退出动画的路线来源随之改变。
    LaunchedEffect(phase) {
        when {
            // 进入专注页不重置，保持选中项与正在进行的旅程一致
            phase == HomePhase.JourneySelection ->
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

    // 退出路线选择时不能再读 timeSelectionUiState.selectedDestination：
    // 回到首页那一刻会把时长重置回最小值、目的地列表被整表替换，
    // 选中项会变成"最接近最小时长"的那条路线（即列表里的第一条），
    // 退出动画就会从它、而不是用户真正选中的那条路线开始推回首页。
    // 与专注路径同理，退出期间改用离开路线选择那一刻的路线快照。
    var lastRouteStations by remember { mutableStateOf<List<Station>?>(null) }
    if (phase == HomePhase.JourneySelection) {
        timeSelectionUiState.selectedDestination?.pathStations?.let { lastRouteStations = it }
    }

    // 稳定回到首页（退出转场结束）后才丢弃快照并重置时长：
    // 推迟到这一刻，退出动画期间才能一直用用户选中的那条路线。
    LaunchedEffect(phase, transitionProgress) {
        if (phase == HomePhase.None && transitionProgress == 0f) {
            lastFocusPath = null
            lastRouteStations = null
            timeSelectionViewModel.resetToMinimum()
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
                focusPath = if (phase == HomePhase.FocusSession) focusUiState.path?.path else lastFocusPath,
                exitRouteStations = lastRouteStations
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
            // 首页层不能一离开 HomePhase.None 就退出组合：那样 location-header /
            // start-journey-button / menu-panel 的 sharedBounds 会瞬间失去形变起点，
            // 只剩一张空地图，路线面板随后才淡入，观感上就是闪现。
            // 包进 AnimatedVisibility 后它会全程参与布局并同步淡出，
            // 共享元素才能像退出时那样从首页位置连续形变到路线面板位置。
            AnimatedVisibility(
                visible = phase == HomePhase.None || isPanelActive,
                enter = EnterTransition.None, // 退出方向保持原样：首页立即完整出现
                exit = fadeOut(tween(HOME_LAYER_FADE_MS)),
                modifier = Modifier.fillMaxSize(),
            ) {
                HomeState(
                    sharedTransitionScope = sharedTransitionScope,
                    // 必须用这层自己的 scope：共享元素只在该 scope 参与转场时才交给共享
                    // 元素层绘制；沿用外层的导航 scope 会留下一份内联副本，与形变中的
                    // 按钮叠在一起，看起来是重影。
                    animatedVisibilityScope = this,
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
                DataBottomSheet(
                    onDismiss = { phase = HomePhase.None },
                    // 先进总旅程地图视图：先把面板收掉，返回首页时不会残留半开的弹层
                    onAllJourneysClick = {
                        phase = HomePhase.None
                        onAllJourneysClick()
                    },
                )
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

private const val HOME_LAYER_FADE_MS = 300

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
    focusPath: List<Station>?,
    exitRouteStations: List<Station>?
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
        // 退出阶段：使用离开路线时留下的路线快照供地图参考（用于退出动画）
        focusPath ?: exitRouteStations
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
