package com.hsr.railfocus.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.util.ChinaBounds
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * MapLibre GL 地图组件
 */
@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    initialPosition: LatLng = LatLng(35.8617, 104.1954),
    // 非空时，“我的位置”标记临时画在该点（如路线选择中手动指定的起点），
    // 相机目标等逻辑仍以 initialPosition 为准；返回后调用方传回 null 即恢复。
    locationMarkerPosition: LatLng? = null,
    initialZoom: Double = 4.0,
    transitionProgress: Float = 0f,
    minZoom: Double = 3.5,
    // 矢量底图只生成到 z10，靠 overzoom 保持高缩放下的清晰度，因此允许放得更深
    maxZoom: Double = 12.0,
    stations: List<Station> = emptyList(),
    showStationMarkers: Boolean = true,
    cameraTargetBounds: List<LatLng> = emptyList(),
    routeStations: List<Station> = emptyList(),
    trainProgress: Float = 0f,
    showTrainMarker: Boolean = false,
    enableGestures: Boolean = true,
    cameraInsetLeftDp: Int = 80,
    cameraInsetTopDp: Int = 80,
    cameraInsetRightDp: Int = 80,
    cameraInsetBottomDp: Int = 80,
    onMapReady: (MapLibreMap) -> Unit = {},
    onMapLoaded: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    
    val isDarkMode = colorScheme.background.toArgb().let { argb ->
        val r = (argb shr 16) and 0xff
        val g = (argb shr 8) and 0xff
        val b = argb and 0xff
        (r * 0.299 + g * 0.587 + b * 0.114) < 128
    }

    val primaryContainerArgb = remember(colorScheme) { colorScheme.primaryContainer.toArgb() }
    val onPrimaryContainerArgb = remember(colorScheme) { colorScheme.onPrimaryContainer.toArgb() }
    val primaryArgb = remember(colorScheme) { colorScheme.primary.toArgb() }

    var styleLoaded by remember { mutableStateOf(false) }
    var mapDestroyed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        MapLibre.getInstance(context)
        onDispose { }
    }

    val mapView = remember {
        MapView(context, org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(Bundle())
            setBackgroundColor(if (isDarkMode) "#1a1a1a".toColorInt() else "#f5f0e8".toColorInt())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                // ON_DESTROY 与 onDispose 都可能触发，保证只销毁一次
                Lifecycle.Event.ON_DESTROY -> {
                    if (!mapDestroyed) {
                        mapDestroyed = true
                        mapView.onDestroy()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!mapDestroyed) {
                mapDestroyed = true
                mapView.onDestroy()
            }
        }
    }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var lastAnimatedPosition by remember { mutableStateOf<LatLng?>(null) }
    var lastAnimatedZoom by remember { mutableStateOf<Double?>(null) }
    var lastAppliedPaddingPx by remember { mutableStateOf<List<Int>?>(null) }
    var lastAppliedGestures by remember { mutableStateOf<Boolean?>(null) }
    var minMaxZoomApplied by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    var latchedBounds by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var latchedStations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var contentReadyForCamera by remember { mutableStateOf(true) }
    var targetCameraState by remember { mutableStateOf<Pair<LatLng, Double>?>(null) }
    var lastFramedBounds by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var lastFramedInsets by remember { mutableStateOf<List<Int>?>(null) }

    // 1. 动态 Padding 管理：随转场进度平滑衰减
    LaunchedEffect(mapInstance, transitionProgress, cameraInsetLeftDp, cameraInsetTopDp, cameraInsetRightDp, cameraInsetBottomDp) {
        val map = mapInstance ?: return@LaunchedEffect
        val density = context.resources.displayMetrics.density
        // 只有在进入路线或完全处于路线中时才应用 Padding。在退出过程中，我们为了动画稳定暂时不更新 Padding
        // 注意：native 调用只在 padding 真正变化时下发，避免打断进行中的相机动画
        val factor = if (transitionProgress > 0.05f) (1f - transitionProgress).coerceIn(0f, 1f) else 1f
        val paddingPx = listOf(
            (cameraInsetLeftDp * density * factor).toInt(),
            (cameraInsetTopDp * density * factor).toInt(),
            (cameraInsetRightDp * density * factor).toInt(),
            (cameraInsetBottomDp * density * factor).toInt(),
        )
        if (paddingPx != lastAppliedPaddingPx) {
            lastAppliedPaddingPx = paddingPx
            map.applyContentPadding(
                left = paddingPx[0],
                top = paddingPx[1],
                right = paddingPx[2],
                bottom = paddingPx[3],
            )
        }
    }

    LaunchedEffect(mapInstance, latchedBounds, cameraInsetLeftDp, cameraInsetTopDp, cameraInsetRightDp, cameraInsetBottomDp) {
        val map = mapInstance ?: return@LaunchedEffect
        if (lastAnimatedPosition == null && latchedBounds.isNotEmpty()) {
            val state = calculateTargetCameraState(
                map = map,
                context = context,
                cameraTargetBounds = latchedBounds,
                cameraInsetLeftDp = cameraInsetLeftDp,
                cameraInsetTopDp = cameraInsetTopDp,
                cameraInsetRightDp = cameraInsetRightDp,
                cameraInsetBottomDp = cameraInsetBottomDp
            )
            state?.let {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(it.first, it.second))
                lastAnimatedPosition = it.first
                lastAnimatedZoom = it.second
            }
        }
    }

    LaunchedEffect(
        cameraTargetBounds,
        routeStations,
        mapInstance,
        cameraInsetLeftDp,
        cameraInsetTopDp,
        cameraInsetRightDp,
        cameraInsetBottomDp
    ) {
        android.util.Log.d("MapCamera", "INPUT: boundsSize=${cameraTargetBounds.size}, transitionProgress=$transitionProgress")
        if (cameraTargetBounds.isNotEmpty()) {
            val isInitialEntry = latchedBounds.isEmpty()
            latchedBounds = cameraTargetBounds
            latchedStations = routeStations
            android.util.Log.d("MapCamera", "LATCHED: data updated")

            mapInstance?.let { map ->
                val state = calculateTargetCameraState(
                    map = map,
                    context = context,
                    cameraTargetBounds = cameraTargetBounds,
                    cameraInsetLeftDp = cameraInsetLeftDp,
                    cameraInsetTopDp = cameraInsetTopDp,
                    cameraInsetRightDp = cameraInsetRightDp,
                    cameraInsetBottomDp = cameraInsetBottomDp
                )
                if (state != null) targetCameraState = state
            }

            if (!isInitialEntry && transitionProgress >= 0.9f) {
                contentReadyForCamera = false
                delay(kotlin.time.Duration.parse("150ms"))
                contentReadyForCamera = true
            } else {
                contentReadyForCamera = true
            }
        }
    }

    LaunchedEffect(transitionProgress) {
        // 关键：不要在这里清除 latchedBounds。
        // 当 transitionProgress 到达 0 时，我们在主 LaunchedEffect 逻辑块的最后统一清除。
        // 这样可以确保退出动画过程中，地图上依然能渲染路线数据。
    }

    var lastFramingTime by remember { mutableLongStateOf(0L) }
    var isMovingToRoute by remember { mutableStateOf(false) }
    var isMovingToHome by remember { mutableStateOf(false) }
    var previousTransitionProgress by remember { mutableFloatStateOf(0f) }
    var wasRouteActive by remember { mutableStateOf(false) }

    // 首页边界约束：相机中心不允许离开 HOME_BOUNDS，拖动/惯性到边界即硬停。
    // 边界固定、不随缩放变化；夹取后中心落在边界内，监听器不会递归触发。
    DisposableEffect(mapInstance) {
        val map = mapInstance
        if (map == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnCameraMoveListener {
                if (transitionProgress < 0.05f && !isMovingToHome && !isMovingToRoute) {
                    val t = map.cameraPosition.target
                    if (t != null) {
                        val lat = t.latitude.coerceIn(HOME_BOUNDS_SOUTH, HOME_BOUNDS_NORTH)
                        val lng = t.longitude.coerceIn(HOME_BOUNDS_WEST, HOME_BOUNDS_EAST)
                        if (lat != t.latitude || lng != t.longitude) {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), map.cameraPosition.zoom)
                            )
                        }
                    }
                }
            }
            map.addOnCameraMoveListener(listener)
            onDispose { map.removeOnCameraMoveListener(listener) }
        }
    }

    // 2. 核心相机指挥部：进入/退出路线或专注时各自只触发一次平滑动画
    LaunchedEffect(
        mapInstance,
        transitionProgress,
        cameraTargetBounds,
        latchedBounds,
        targetCameraState,
        contentReadyForCamera,
        cameraInsetLeftDp,
        cameraInsetTopDp,
        cameraInsetRightDp,
        cameraInsetBottomDp,
        enableGestures,
        initialPosition
    ) {
        val map = mapInstance ?: return@LaunchedEffect
        val currentTime = System.currentTimeMillis()
        // 目标位置需要夹在首页相机边界内：像双鸭山西这样位于边界之外的车站，
        // 相机实际上无法居中到它，而是停靠在边界边缘。如果直接用未夹取的车站坐标
        // 作为动画终点，相机会先移过去、待边界重新生效时再跳变回来。
        // 夹取后，返回动画会平滑地上下左右平移到最终停靠位置。
        val homeTarget = clampToHomeTarget(initialPosition)
        val currentMapPos = map.cameraPosition

        // 核心相机指挥部：进入/退出路线或专注时各自只触发一次平滑动画
        if (transitionProgress <= 0f) {
            val dist = homeTarget.distanceTo(currentMapPos.target ?: homeTarget)
            val zoomDiff = kotlin.math.abs(initialZoom - currentMapPos.zoom)

            if (dist > 1.0 || zoomDiff > 0.01) {
                // 如果是从路线退出，或者当前不在首页位置，执行平滑动画
                if (wasRouteActive) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(homeTarget, initialZoom), 500)
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(homeTarget, initialZoom))
                }
            }
            lastAnimatedPosition = homeTarget
            lastAnimatedZoom = initialZoom
            isMovingToRoute = false
            isMovingToHome = false
            wasRouteActive = false
            // 关键修正：只有在当前确实没有路线数据（真正回到首页）时才清空 latched 数据。
            // 重新进入路线选择时，上一帧 latch 效应可能已经写入了新的路线 bounds，
            // 而转场进度此时仍是 0f；若在这里无条件清空，地图会永远卡在首页默认视角。
            if (cameraTargetBounds.isEmpty()) {
                latchedBounds = emptyList()
                latchedStations = emptyList()
                targetCameraState = null
            }
            previousTransitionProgress = transitionProgress
            return@LaunchedEffect
        }

        // 检测方向变化：进入或退出
        val isEntering = transitionProgress > previousTransitionProgress
        val isExiting = transitionProgress < previousTransitionProgress

        if (isEntering && !isMovingToRoute && latchedBounds.isNotEmpty()) {
            isMovingToRoute = true
            isMovingToHome = false
            wasRouteActive = true

            frameCamera(
                map = map,
                context = context,
                cameraTargetBounds = latchedBounds,
                cameraInsetLeftDp = cameraInsetLeftDp,
                cameraInsetTopDp = cameraInsetTopDp,
                cameraInsetRightDp = cameraInsetRightDp,
                cameraInsetBottomDp = cameraInsetBottomDp,
                animate = true,
                duration = 600
            )
            previousTransitionProgress = transitionProgress
            return@LaunchedEffect
        }

        if (isExiting && !isMovingToHome) {
            isMovingToHome = true
            isMovingToRoute = false
            wasRouteActive = true

            map.easeCamera(CameraUpdateFactory.newLatLngZoom(homeTarget, initialZoom), 500)
            previousTransitionProgress = transitionProgress
            return@LaunchedEffect
        }

        if (transitionProgress >= 1f) {
            if (latchedBounds.size >= 2 && contentReadyForCamera) {
                val isFocusSession = !enableGestures
                val routeZoom = targetCameraState?.second ?: 7.0
                val isLongEnoughForFollow = routeZoom < 7.5

                // 只有在非跟随模式下才执行 frameCamera
                if (!isFocusSession || !isLongEnoughForFollow) {
                    val boundsChanged = latchedBounds != lastFramedBounds
                    val currentInsets = listOf(cameraInsetLeftDp, cameraInsetTopDp, cameraInsetRightDp, cameraInsetBottomDp)
                    val insetsChanged = lastFramedInsets != null && lastFramedInsets != currentInsets
                    val currentTarget = targetCameraState?.first ?: latchedBounds.first()
                    val currentZoom = targetCameraState?.second ?: 7.0
                    val dist = currentTarget.distanceTo(currentMapPos.target ?: homeTarget)
                    val zoomDiff = kotlin.math.abs(currentZoom - currentMapPos.zoom)

                    if (boundsChanged || insetsChanged || (dist > 2000.0) || (zoomDiff > 0.2)) {
                        android.util.Log.d("MapCamera", "Framing camera: latchedBounds size=${latchedBounds.size}")
                        frameCamera(
                            map = map,
                            context = context,
                            cameraTargetBounds = latchedBounds,
                            cameraInsetLeftDp = cameraInsetLeftDp,
                            cameraInsetTopDp = cameraInsetTopDp,
                            cameraInsetRightDp = cameraInsetRightDp,
                            cameraInsetBottomDp = cameraInsetBottomDp,
                            animate = true,
                            duration = 800
                        )
                        lastFramingTime = currentTime
                        lastFramedBounds = latchedBounds
                        lastFramedInsets = currentInsets
                    }
                }
                lastAnimatedZoom = map.cameraPosition.zoom
                lastAnimatedPosition = map.cameraPosition.target
            }
        }

        previousTransitionProgress = transitionProgress
    }

    // 3. 专注模式跟随逻辑 - 独立 LaunchedEffect，仅依赖 trainProgress
    LaunchedEffect(
        mapInstance,
        trainProgress,
        transitionProgress,
        enableGestures,
        latchedStations,
        targetCameraState
    ) {
        val map = mapInstance ?: return@LaunchedEffect
        if (transitionProgress >= 1f && !enableGestures && latchedStations.size >= 2) {
            val routeZoom = targetCameraState?.second ?: 7.0
            val isLongEnoughForFollow = routeZoom < 7.5

            if (isLongEnoughForFollow) {
                val (trainPos, _) = interpolatePositionAndBearingAlongRoute(latchedStations, trainProgress.coerceIn(0f, 1f))
                val followZoom = 8.2

                try {
                    if (trainProgress >= 0.95f) {
                        val target = latchedStations.last().let { LatLng(it.lat, it.lng) }
                        android.util.Log.d("MapCamera", "Following train: approach destination $target")
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 8.5), 1000)
                    } else {
                        android.util.Log.d("MapCamera", "Following train: pos=$trainPos, progress=$trainProgress")
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(trainPos, followZoom), 1000)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // 记录上一次应用的模式，用于在 update 时强制刷新样式
    var lastAppliedDarkMode by remember { mutableStateOf<Boolean?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                mapInstance = map
                if (enableGestures != lastAppliedGestures) {
                    lastAppliedGestures = enableGestures
                    map.uiSettings.apply {
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                        isScrollGesturesEnabled = enableGestures
                        isZoomGesturesEnabled = enableGestures
                        isCompassEnabled = false
                        isLogoEnabled = false
                        isAttributionEnabled = false
                    }
                }
                if (minMaxZoomApplied != (minZoom to maxZoom)) {
                    minMaxZoomApplied = minZoom to maxZoom
                    map.setMinZoomPreference(minZoom)
                    map.setMaxZoomPreference(maxZoom)
                }

                // 首页边界不用 setLatLngBoundsForCameraTarget：
                // 惯性滑动（fling）会走 easeTo 的视口约束，屏幕大于边界时
                // 会强制放大并把相机锁死在边界中心。改为手动夹取（见下方监听器）。

                val currentStyle = map.style
                val needsStyleReload = currentStyle == null || 
                                     !currentStyle.isFullyLoaded || 
                                     styleLoaded != isDarkMode ||
                                     lastAppliedDarkMode != isDarkMode

                if (needsStyleReload) {
                    android.util.Log.d("MapLibreView", "Reloading style: isDarkMode=$isDarkMode")
                    lastAppliedDarkMode = isDarkMode
                    map.setStyle(Style.Builder().fromJson(createMinimalOSMStyle(isDarkMode))) { style ->
                        styleLoaded = isDarkMode
                        updateMapLayers(
                            style = style,
                            context = context,
                            initialPosition = locationMarkerPosition ?: initialPosition,
                            stations = stations,
                            showStationMarkers = showStationMarkers,
                            latchedStations = latchedStations,
                            trainProgress = trainProgress,
                            showTrainMarker = showTrainMarker,
                            primaryContainerArgb = primaryContainerArgb,
                            onPrimaryContainerArgb = onPrimaryContainerArgb,
                            primaryArgb = primaryArgb,
                            onMapLoaded = onMapLoaded,
                            transitionProgress = transitionProgress,
                        )
                        onMapReady(map)
                    }
                } else {
                    updateMapLayers(
                        style = currentStyle,
                        context = context,
                        initialPosition = locationMarkerPosition ?: initialPosition,
                        stations = stations,
                        showStationMarkers = showStationMarkers,
                        latchedStations = latchedStations,
                        trainProgress = trainProgress,
                        showTrainMarker = showTrainMarker,
                        primaryContainerArgb = primaryContainerArgb,
                        onPrimaryContainerArgb = onPrimaryContainerArgb,
                        primaryArgb = primaryArgb,
                        onMapLoaded = onMapLoaded,
                        transitionProgress = transitionProgress,
                    )
                    onMapReady(map)
                }
            }
        }
    )
}

/** 首页相机目标的可行范围（大致覆盖中国） */
private const val HOME_BOUNDS_NORTH = 41.0
private const val HOME_BOUNDS_EAST = 131.0
private const val HOME_BOUNDS_SOUTH = 22.0
private const val HOME_BOUNDS_WEST = 80.0

/**
 * 把目标位置夹取到首页相机边界内。超出边界的车站（如双鸭山西）无法被相机居中，
 * 相机最终停靠在边界边缘，动画也应以该夹取位置为终点，避免结束时跳变。
 */
private fun clampToHomeTarget(target: LatLng): LatLng = LatLng(
    target.latitude.coerceIn(HOME_BOUNDS_SOUTH, HOME_BOUNDS_NORTH),
    target.longitude.coerceIn(HOME_BOUNDS_WEST, HOME_BOUNDS_EAST),
)

private fun updateMapLayers(
    style: Style,
    context: android.content.Context,
    initialPosition: LatLng,
    stations: List<Station>,
    showStationMarkers: Boolean,
    latchedStations: List<Station>,
    trainProgress: Float,
    showTrainMarker: Boolean,
    primaryContainerArgb: Int,
    onPrimaryContainerArgb: Int,
    primaryArgb: Int,
    onMapLoaded: () -> Unit,
    transitionProgress: Float = 1.0f,
) {
    // 透明度始终跟随转场进度：进入时淡入，返回时随缩放一起渐出，
    // 这样路线和站点元素会在动画过程中慢慢消失，而不是在结束时瞬间消失。
    val contentOpacity = transitionProgress.coerceIn(0f, 1f)
    
    android.util.Log.d("MapLayers", "RENDER: opacity=$contentOpacity")
    if (latchedStations.size >= 2) {
        addRoutePolyline(style, latchedStations, primaryArgb, contentOpacity)
    } else {
        removeSourceAndLayer(style, "route-source", "route-layer")
    }
    updateCurrentLocationMarker(style, initialPosition)
    // 修正：确保在有 routeStations 时，即使整体 showStationMarkers 为 false，
    // 依然显示起始站、终点站和中间站
    val startStationId = latchedStations.firstOrNull()?.id
    val endStationId = latchedStations.lastOrNull()?.id
    val intermediateStations = if (latchedStations.size > 2) {
        latchedStations.subList(1, latchedStations.size - 1)
    } else emptyList()
    
    // 如果 showStationMarkers 为 true，显示所有 background stations + intermediate
    // 如果为 false，仅显示中间站 (用于路线高亮)
    val backgroundMarkers = if (showStationMarkers) {
        stations.filter { station ->
            station.id != startStationId && 
            station.id != endStationId &&
            (station.lat != initialPosition.latitude || station.lng != initialPosition.longitude)
        }
    } else emptyList()
    
    val allMarkersToShow = (intermediateStations + backgroundMarkers).distinctBy { it.id }
    addStationMarkers(context, style, allMarkersToShow, true, contentOpacity) // 强制为 true，由 list 控制显示内容

    if (latchedStations.size >= 2) {
        val destination = latchedStations.last()
        addDestinationMarker(context, style, destination, contentOpacity)
    } else {
        removeSourceAndLayer(style, "destination-source", "destination-layer")
    }
    if (latchedStations.size >= 2) {
        addRouteStationPills(context, style, latchedStations, primaryContainerArgb, onPrimaryContainerArgb, primaryArgb, primaryArgb, contentOpacity)
    } else {
        removeSourceAndLayer(style, "highlighted-pill-source", "highlighted-pill-layer")
    }
    if (showTrainMarker && latchedStations.size >= 2) {
        updateTrainMarker(context, style, latchedStations, trainProgress, showTrainMarker, contentOpacity)
    } else {
        removeSourceAndLayer(style, "train-source", "train-layer")
    }
    onMapLoaded()
}

private fun frameCamera(
    map: MapLibreMap,
    context: android.content.Context,
    cameraTargetBounds: List<LatLng>,
    cameraInsetLeftDp: Int,
    cameraInsetTopDp: Int,
    cameraInsetRightDp: Int,
    cameraInsetBottomDp: Int,
    animate: Boolean = false,
    duration: Int = 1000,
    forcedZoom: Double? = null
) {
    if (cameraTargetBounds.isEmpty()) return
    
    val density = context.resources.displayMetrics.density

    // 增加内部基础边距，确保即使在极窄屏幕上也不贴边
    val baseMarginDp = 24
    val pLeft = ((cameraInsetLeftDp + baseMarginDp) * density).toInt()
    val pTop = ((cameraInsetTopDp + baseMarginDp) * density).toInt()
    val pRight = ((cameraInsetRightDp + baseMarginDp) * density).toInt()
    val pBottom = ((cameraInsetBottomDp + baseMarginDp) * density).toInt()

    val boundsBuilder = LatLngBounds.Builder()
    cameraTargetBounds.forEach { latLng -> boundsBuilder.include(latLng) }
    val latLngBounds = boundsBuilder.build()

    val update = try {
        if (forcedZoom != null) {
            CameraUpdateFactory.newLatLngZoom(latLngBounds.center, forcedZoom)
        } else if (cameraTargetBounds.size < 2 || latLngBounds.southWest == latLngBounds.northEast) {
            CameraUpdateFactory.newLatLngZoom(latLngBounds.center, 8.0)
        } else {
            // 使用标准的 newLatLngBounds。它比 getCameraForLatLngBounds 更直接
            // 且不会因为 padding 设置过大而轻易返回 null
            CameraUpdateFactory.newLatLngBounds(latLngBounds, pLeft, pTop, pRight, pBottom)
        }
    } catch (e: Exception) {
        android.util.Log.e("MapCamera", "frameCamera error: ${e.message}")
        CameraUpdateFactory.newLatLngZoom(latLngBounds.center, 7.0)
    }

    if (animate) {
        map.easeCamera(update, duration, false)
    } else {
        map.moveCamera(update)
    }
}

/**
 * MapLibreMap.setPadding 在 13.3.1 中已废弃，但替代的
 * Projection#setContentPadding 是包级私有方法，应用侧无法调用，
 * 因此暂时保留旧 API 并抑制告警，待 SDK 提供公开替代后再迁移。
 */
@Suppress("DEPRECATION")
private fun MapLibreMap.applyContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
    setPadding(left, top, right, bottom)
}

private fun calculateTargetCameraState(
    map: MapLibreMap,
    context: android.content.Context,
    cameraTargetBounds: List<LatLng>,
    cameraInsetLeftDp: Int,
    cameraInsetTopDp: Int,
    cameraInsetRightDp: Int,
    cameraInsetBottomDp: Int
): Pair<LatLng, Double>? {
    if (cameraTargetBounds.isEmpty()) return null
    val density = context.resources.displayMetrics.density
    val edgeMarginDp = 16
    val pLeft = ((cameraInsetLeftDp + edgeMarginDp) * density).toInt()
    val pTop = ((cameraInsetTopDp + edgeMarginDp) * density).toInt()
    val pRight = ((cameraInsetRightDp + edgeMarginDp) * density).toInt()
    val pBottom = ((cameraInsetBottomDp + edgeMarginDp) * density).toInt()
    val boundsBuilder = LatLngBounds.Builder()
    cameraTargetBounds.forEach { latLng -> boundsBuilder.include(latLng) }
    val latLngBounds = if (cameraTargetBounds.size >= 2) { boundsBuilder.build() } else { null }
    return try {
        if (latLngBounds == null) return null
        val position = map.getCameraForLatLngBounds(latLngBounds, intArrayOf(pLeft, pTop, pRight, pBottom))
        if (position != null) { position.target!! to position.zoom } else { latLngBounds.center to 7.0 }
    } catch (_: Exception) { null }
}

private fun removeSourceAndLayer(style: Style, sourceId: String, layerId: String) {
    style.getLayer(layerId)?.let { style.removeLayer(it) }
    style.getSource(sourceId)?.let { style.removeSource(it) }
}

private fun updateCurrentLocationMarker(style: Style, position: LatLng) {
    if (position.latitude == 0.0 && position.longitude == 0.0) return // 防止在位置未加载时显示在原点

    val geoJson = JSONObject().apply {
        put("type", "Point")
        put("coordinates", JSONArray().apply { put(position.longitude); put(position.latitude) })
    }
    val existingSource = style.getSourceAs<GeoJsonSource>("current-location-source")
    if (existingSource != null) {
        existingSource.setGeoJson(geoJson.toString())
    } else {
        val source = GeoJsonSource("current-location-source", geoJson.toString())
        style.addSource(source)
        val outerCircle = CircleLayer("current-location-outer", "current-location-source")
        outerCircle.withProperties(
            PropertyFactory.circleRadius(20f),
            PropertyFactory.circleColor("#81C784".toColorInt()),
            PropertyFactory.circleOpacity(0.3f),
            PropertyFactory.circleBlur(1f)
        )
        style.addLayer(outerCircle)
        val innerCircle = CircleLayer("current-location-inner", "current-location-source")
        innerCircle.withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#2E7D32".toColorInt()),
            PropertyFactory.circleOpacity(1f),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f)
        )
        style.addLayer(innerCircle)
    }
}

private fun addStationMarkers(
    context: android.content.Context,
    style: Style,
    stations: List<Station>,
    visible: Boolean,
    opacity: Float = 1.0f
) {
    if (!visible || stations.isEmpty()) {
        removeSourceAndLayer(style, "stations-source", "stations-layer")
        return
    }
    val pinBitmap = createVectorIconBitmap(context, R.drawable.ic_location_pin, androidx.compose.ui.graphics.Color.Unspecified, 22)
    style.addImage("station-pin-marker", pinBitmap)
    val features = JSONArray().apply {
        stations.forEach { station ->
            put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply { put(station.lng); put(station.lat) })
                })
            })
        }
    }
    val geoJson = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
    val existingSource = style.getSourceAs<GeoJsonSource>("stations-source")
    if (existingSource != null) {
        existingSource.setGeoJson(geoJson)
    } else {
        val source = GeoJsonSource("stations-source", geoJson)
        style.addSource(source)
        val layer = SymbolLayer("stations-layer", "stations-source")
        layer.withProperties(
            PropertyFactory.iconImage("station-pin-marker"),
            PropertyFactory.iconAnchor("bottom"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconOpacity(opacity)
        )
        style.addLayer(layer)
    }
    style.getLayer("stations-layer")?.setProperties(PropertyFactory.iconOpacity(opacity))
}

private fun addDestinationMarker(
    context: android.content.Context,
    style: Style,
    destination: Station,
    opacity: Float = 1.0f
) {
    val pinBitmap = createVectorIconBitmap(context, R.drawable.ic_destination_marker, androidx.compose.ui.graphics.Color.Unspecified, 28)
    style.addImage("destination-pin-marker", pinBitmap)
    val geoJson = JSONObject().apply {
        put("type", "Feature")
        put("geometry", JSONObject().apply {
            put("type", "Point")
            put("coordinates", JSONArray().apply { put(destination.lng); put(destination.lat) })
        })
    }.toString()
    val existingSource = style.getSourceAs<GeoJsonSource>("destination-source")
    if (existingSource != null) {
        existingSource.setGeoJson(geoJson)
    } else {
        val source = GeoJsonSource("destination-source", geoJson)
        style.addSource(source)
        val layer = SymbolLayer("destination-layer", "destination-source")
        layer.withProperties(
            PropertyFactory.iconImage("destination-pin-marker"),
            PropertyFactory.iconAnchor("bottom"),
            PropertyFactory.iconOffset(arrayOf(0f, 4f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconOpacity(opacity)
        )
        style.addLayer(layer)
    }
    style.getLayer("destination-layer")?.setProperties(PropertyFactory.iconOpacity(opacity))
}

private fun addRoutePolyline(
    style: Style,
    stations: List<Station>,
    lineColor: Int,
    opacity: Float = 1.0f
): List<Station> {
    val coordinates = JSONArray().apply {
        stations.forEach { station ->
            put(JSONArray().apply { put(station.lng); put(station.lat) })
        }
    }
    val geoJson = JSONObject().apply {
        put("type", "Feature")
        put("geometry", JSONObject().apply { put("type", "LineString"); put("coordinates", coordinates) })
    }.toString()
    val existingSource = style.getSourceAs<GeoJsonSource>("route-source")
    if (existingSource != null) {
        existingSource.setGeoJson(geoJson)
    } else {
        val source = GeoJsonSource("route-source", geoJson)
        style.addSource(source)
        val lineLayer = LineLayer("route-layer", "route-source")
        lineLayer.withProperties(
            PropertyFactory.lineColor(lineColor),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineOpacity(opacity * 0.6f),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
        )
        style.addLayer(lineLayer)
    }
    style.getLayer("route-layer")?.setProperties(PropertyFactory.lineOpacity(opacity * 0.6f))
    return stations
}

private fun updateTrainMarker(
    context: android.content.Context,
    style: Style,
    routeStations: List<Station>,
    progress: Float,
    showTrainMarker: Boolean,
    opacity: Float = 1.0f
) {
    if (!showTrainMarker || routeStations.size < 2) {
        removeSourceAndLayer(style, "train-source", "train-layer")
        return
    }
    val (position, bearing) = interpolatePositionAndBearingAlongRoute(routeStations, progress.coerceIn(0f, 1f))
    val existingImage = style.getImage("train-marker")
    if (existingImage == null) {
        val trainBitmap = createVectorIconBitmap(context, R.drawable.ic_train_arrow_marker, androidx.compose.ui.graphics.Color.Unspecified, 24)
        style.addImage("train-marker", trainBitmap)
    }
    val geoJson = JSONObject().apply {
        put("type", "Point")
        put("coordinates", JSONArray().apply { put(position.longitude); put(position.latitude) })
    }.toString()
    val existingSource = style.getSourceAs<GeoJsonSource>("train-source")
    if (existingSource != null) {
        existingSource.setGeoJson(geoJson)
    } else {
        val source = GeoJsonSource("train-source", geoJson)
        style.addSource(source)
        val layer = SymbolLayer("train-layer", "train-source")
        layer.withProperties(
            PropertyFactory.iconImage("train-marker"),
            PropertyFactory.iconAnchor("center"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconRotationAlignment("map"),
            PropertyFactory.iconRotate(bearing),
            PropertyFactory.iconOpacity(opacity)
        )
        style.addLayer(layer)
    }
    style.getLayer("train-layer")?.setProperties(PropertyFactory.iconOpacity(opacity), PropertyFactory.iconRotate(bearing))
}

private fun calculateBearing(start: LatLng, end: LatLng): Float {
    val lat1 = Math.toRadians(start.latitude); val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.toRadians(end.latitude); val lon2 = Math.toRadians(end.longitude)
    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    var brng = Math.atan2(y, x)
    brng = Math.toDegrees(brng)
    return ((brng + 360) % 360).toFloat()
}

private fun interpolatePositionAndBearingAlongRoute(
    routeStations: List<Station>,
    progress: Float,
): Pair<LatLng, Float> {
    if (routeStations.size < 2) {
        val station = routeStations.firstOrNull()
        return LatLng(station?.lat ?: 0.0, station?.lng ?: 0.0) to 0f
    }
    val segmentCount = routeStations.size - 1
    val rawSegmentIndex = progress * segmentCount
    val segmentIndex = rawSegmentIndex.toInt().coerceIn(0, segmentCount - 1)
    val segmentProgress = (rawSegmentIndex - segmentIndex).coerceIn(0f, 1f)
    val start = routeStations[segmentIndex]
    val end = routeStations[segmentIndex + 1]
    val startLatLng = LatLng(start.lat, start.lng)
    val endLatLng = LatLng(end.lat, end.lng)
    val lat = start.lat + (end.lat - start.lat) * segmentProgress
    val lng = start.lng + (end.lng - start.lng) * segmentProgress
    val bearing = calculateBearing(startLatLng, endLatLng)
    return LatLng(lat, lng) to bearing
}

private fun createVectorIconBitmap(
    context: android.content.Context,
    drawableRes: Int,
    tintColor: androidx.compose.ui.graphics.Color,
    sizeDp: Int,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableRes)
        ?: return createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val tinted = if (tintColor != androidx.compose.ui.graphics.Color.Unspecified) {
        drawable.mutate().apply { setTint(tintColor.toArgb()) }
    } else drawable
    tinted.setBounds(0, 0, sizePx, sizePx)
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap); tinted.draw(canvas)
    return bitmap
}

private fun addRouteStationPills(
    context: android.content.Context,
    style: Style,
    stations: List<Station>,
    backgroundColor: Int,
    textColor: Int,
    dotColor: Int,
    borderColor: Int,
    opacity: Float = 1.0f
) {
    if (stations.isEmpty()) { removeSourceAndLayer(style, "highlighted-pill-source", "highlighted-pill-layer"); return }
    stations.forEach { station ->
        val imageId = "pill-${station.id}-$backgroundColor-$textColor"
        if (style.getImage(imageId) == null) {
            val bitmap = createPillBitmap(context, station.name, backgroundColor, textColor, dotColor, borderColor)
            style.addImage(imageId, bitmap)
        }
    }
    val features = JSONArray().apply {
        stations.forEach { station ->
            val imageId = "pill-${station.id}-$backgroundColor-$textColor"
            put(JSONObject().apply {
                put("type", "Feature")
                put("properties", JSONObject().apply { put("image-id", imageId) })
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply { put(station.lng); put(station.lat) })
                })
            })
        }
    }
    val geoJson = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
    val existingSource = style.getSourceAs<GeoJsonSource>("highlighted-pill-source")
    if (existingSource != null) {
        existingSource.setGeoJson(geoJson)
    } else {
        val source = GeoJsonSource("highlighted-pill-source", geoJson)
        style.addSource(source)
        val symbolLayer = SymbolLayer("highlighted-pill-layer", "highlighted-pill-source")
        symbolLayer.withProperties(
            PropertyFactory.iconImage("{image-id}"),
            PropertyFactory.iconAnchor("bottom"),
            PropertyFactory.iconOffset(arrayOf(0f, -36f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconOpacity(opacity)
        )
        style.addLayer(symbolLayer)
    }
    style.getLayer("highlighted-pill-layer")?.setProperties(PropertyFactory.iconOpacity(opacity))
}

private fun createPillBitmap(
    context: android.content.Context,
    text: String,
    backgroundColor: Int,
    textColor: Int,
    dotColor: Int,
    borderColor: Int?,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = 12f * density; this.color = textColor }
    val dotSize = (6f * density).toInt(); val dotGap = (6f * density).toInt()
    val paddingX = (12f * density).toInt(); val paddingY = (6f * density).toInt()
    val textBounds = Rect(); textPaint.getTextBounds(text, 0, text.length, textBounds)
    val width = textBounds.width() + paddingX * 2 + dotSize + dotGap
    val height = maxOf(textBounds.height(), dotSize) + paddingY * 2
    val cornerRadius = height / 2f
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000; maskFilter = android.graphics.BlurMaskFilter(2f * density, android.graphics.BlurMaskFilter.Blur.NORMAL) }
    val shadowRect = RectF(1f * density, 1f * density, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = backgroundColor }
    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)
    borderColor?.let { color ->
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.style = Paint.Style.STROKE; this.strokeWidth = 1f * density }
        val strokeRect = RectF(borderPaint.strokeWidth / 2f, borderPaint.strokeWidth / 2f, width.toFloat() - borderPaint.strokeWidth / 2f, height.toFloat() - borderPaint.strokeWidth / 2f)
        canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, borderPaint)
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = dotColor }
    val dotCx = (paddingX + dotSize / 2f); val dotCy = height / 2f
    canvas.drawCircle(dotCx, dotCy, dotSize / 2f, dotPaint)
    val textX = paddingX + dotSize + dotGap.toFloat(); val textY = height / 2f - textBounds.exactCenterY()
    canvas.drawText(text, textX, textY, textPaint)
    return bitmap
}

private fun createMinimalOSMStyle(isDarkMode: Boolean): String {
    // 矢量底图（OSM 数据经 Planetiler 生成，z4-10，asset 离线打包）。
    // 亮色为米色纸感：浅蓝水系、浅灰道路、深灰铁路；暗色整体压暗。
    val bgColor = if (isDarkMode) "#1a1a1a" else "#f5f0e8"
    val waterColor = if (isDarkMode) "#16303c" else "#c8dfe8"
    val waterwayColor = if (isDarkMode) "#234a5c" else "#a8c8d8"
    val roadColor = if (isDarkMode) "#3a3a3a" else "#e2dbd0"
    val railColor = if (isDarkMode) "#8a8a8a" else "#969696"
    return JSONObject().apply {
        put("version", 8)
        put("sources", JSONObject().apply {
            put("base", JSONObject().apply {
                put("type", "vector")
                put("tiles", JSONArray().apply { put("asset://tiles_vector/{z}/{x}/{y}.pbf") })
                put("minzoom", 4); put("maxzoom", 10)
            })
        })
        put("layers", JSONArray().apply {
            put(JSONObject().apply { put("id", "background"); put("type", "background"); put("paint", JSONObject().apply { put("background-color", bgColor) }) })
            put(JSONObject().apply {
                put("id", "water"); put("type", "fill"); put("source", "base"); put("source-layer", "water")
                put("paint", JSONObject().apply { put("fill-color", waterColor) })
            })
            put(JSONObject().apply {
                put("id", "waterway"); put("type", "line"); put("source", "base"); put("source-layer", "waterway")
                put("filter", JSONArray().apply {
                    put("any")
                    put(JSONArray().apply { put("=="); put("class"); put("river") })
                    put(JSONArray().apply { put("=="); put("class"); put("canal") })
                })
                put("paint", JSONObject().apply {
                    put("line-color", waterwayColor)
                    put("line-width", if (isDarkMode) 1.2 else 1.0)
                })
            })
            put(JSONObject().apply {
                put("id", "road"); put("type", "line"); put("source", "base"); put("source-layer", "transportation")
                put("filter", JSONArray().apply {
                    put("all")
                    put(JSONArray().apply { put("!="); put("class"); put("rail") })
                    put(JSONArray().apply { put("!="); put("class"); put("transit") })
                })
                put("paint", JSONObject().apply {
                    put("line-color", roadColor)
                    put("line-width", JSONObject().apply {
                        put("base", 1.4)
                        put("stops", JSONArray().apply {
                            put(JSONArray().apply { put(4); put(0.4) })
                            put(JSONArray().apply { put(8); put(0.8) })
                            put(JSONArray().apply { put(12); put(2.0) })
                        })
                    })
                })
            })
            put(JSONObject().apply {
                put("id", "rail"); put("type", "line"); put("source", "base"); put("source-layer", "transportation")
                put("filter", JSONArray().apply { put("=="); put("class"); put("rail") })
                put("paint", JSONObject().apply {
                    put("line-color", railColor)
                    put("line-width", JSONObject().apply {
                        put("base", 1.2)
                        put("stops", JSONArray().apply {
                            put(JSONArray().apply { put(4); put(0.5) })
                            put(JSONArray().apply { put(8); put(1.0) })
                            put(JSONArray().apply { put(12); put(1.6) })
                        })
                    })
                })
            })
        })
    }.toString()
}
