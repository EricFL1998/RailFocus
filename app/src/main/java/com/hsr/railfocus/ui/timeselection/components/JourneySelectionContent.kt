package com.hsr.railfocus.ui.timeselection.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.service.DestinationOption
import com.hsr.railfocus.ui.components.BulletTrainIcon
import com.hsr.railfocus.ui.timeselection.TimeSelectionUiState
import org.maplibre.android.geometry.LatLng

/**
 * 旅程选择内容
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun JourneySelectionContent(
    uiState: TimeSelectionUiState,
    searchQuery: String,
    searchResults: List<Station>,
    onBack: () -> Unit,
    onStartFocus: (DestinationOption) -> Unit,
    onShowStationSearch: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onDestinationSelected: (Int) -> Unit,
    showMap: Boolean = true,
    showBackArrow: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBottomPanelHeightChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var bottomPanelSize by remember { mutableStateOf(IntSize.Zero) }
    var topPanelSize by remember { mutableStateOf(IntSize.Zero) }
    
    // 搜索状态管理
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val bottomPanelHeightDp = remember(bottomPanelSize) {
        with(density) { bottomPanelSize.height.toDp() }
    }
    val topPanelHeightDp = remember(topPanelSize) {
        with(density) { topPanelSize.height.toDp() }
    }
    val bottomPanelHeightWithMargin = bottomPanelHeightDp.value.toInt() + 8
    val topPanelHeightWithMargin = topPanelHeightDp.value.toInt() + 8

    LaunchedEffect(bottomPanelHeightWithMargin) {
        // 关键：只有当高度真正改变且不为 0 时才通知外部，避免初次加载时的抖动
        if (bottomPanelHeightWithMargin > 8) {
            onBottomPanelHeightChanged?.invoke(bottomPanelHeightWithMargin)
        }
    }

    // 监听返回键收起搜索
    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        onSearchQueryChanged("")
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 地图背景
        if (showMap) {
            val startLatLng = LatLng(uiState.startStation.lat, uiState.startStation.lng)
            val routeStations = uiState.selectedDestination?.pathStations ?: emptyList()
            val cameraTargetBounds = routeStations.map { LatLng(it.lat, it.lng) }
            val sideInsetDp = 24

            com.hsr.railfocus.ui.components.MapLibreView(
                modifier = Modifier.fillMaxSize(),
                initialPosition = startLatLng,
                initialZoom = 7.0,
                minZoom = 5.0,
                maxZoom = 12.0,
                stations = routeStations.drop(1),
                cameraTargetBounds = cameraTargetBounds,
                routeStations = routeStations,
                cameraInsetLeftDp = sideInsetDp,
                cameraInsetTopDp = topPanelHeightWithMargin,
                cameraInsetRightDp = sideInsetDp,
                cameraInsetBottomDp = bottomPanelHeightWithMargin,
            )
        }

        // 2. 顶部搜索栏与操作区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .onGloballyPositioned { coordinates ->
                    topPanelSize = coordinates.size
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 动画搜索框
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        // 关键修正：从右向左展开 (expandFrom = Alignment.End)
                        // 这样动画会从搜索按钮所在的右侧位置“长出来”
                        (fadeIn(tween(300)) + 
                         expandHorizontally(
                             expandFrom = Alignment.End,
                             animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.8f)
                         )) togetherWith 
                        (fadeOut(tween(200)) + 
                         shrinkHorizontally(
                             shrinkTowards = Alignment.End,
                             animationSpec = tween(250)
                         ))
                    },
                    label = "search_bar_anim"
                ) { active ->
                    if (active) {
                        // 展开状态的搜索框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.selection_search_placeholder), style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = {
                                IconButton(onClick = { isSearchActive = false; onSearchQueryChanged("") }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.selection_cancel_search))
                                }
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.selection_clear))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    } else {
                        // 初始状态：返回按钮 + 搜索图标
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showBackArrow) {
                                IconButton(
                                    onClick = onBack,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.selection_back),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(48.dp))
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Surface(
                                onClick = { isSearchActive = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface, // 使用标准表面色
                                tonalElevation = 3.dp, // 增加高度感
                                shadowElevation = 4.dp, // 增加阴影，在暗色地图上更清晰
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.search_expand_cd),
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary // 使用主色调，确保高亮可见
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 联想结果列表 (在搜索激活时显示)
            if (isSearchActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.search_no_result), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { station ->
                                StationCard(
                                    station = station,
                                    onClick = {
                                        onStationSelected(station)
                                        isSearchActive = false
                                        focusManager.clearFocus()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 底部面板
        AnimatedVisibility(
            visible = !isSearchActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        bottomPanelSize = coordinates.size
                    }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        TimeDurationPicker(
                            minDuration = uiState.minDuration,
                            maxDuration = uiState.maxDuration,
                            selectedDuration = uiState.selectedDuration,
                            onDurationSelected = onDurationSelected
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    DestinationList(
                        destinations = uiState.destinations,
                        selectedIndex = uiState.selectedDestinationIndex,
                        isLoading = uiState.isCalculating,
                        onDestinationSelected = onDestinationSelected
                    )
                }

                Button(
                    onClick = {
                        val selected = uiState.selectedDestination
                        if (selected != null) {
                            onStartFocus(selected)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = "start-journey-button"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
                    enabled = uiState.selectedDuration != null && uiState.selectedDestination != null,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    BulletTrainIcon(
                        contentDescription = null,
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_start_journey),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
