package com.hsr.railfocus.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Box
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
    var appliedHomeMinZoom by remember { mutableStateOf<Double?>(null) }

    // 相机监听器里按值捕获 Compose 参数会拿到注册时的旧值，全部经 rememberUpdatedState 读取
    val transitionProgressRef = rememberUpdatedState(transitionProgress)
    val locationMarkerRef = rememberUpdatedState(locationMarkerPosition)
    val initialPositionRef = rememberUpdatedState(initialPosition)
    val insetsRef = rememberUpdatedState(
        listOf(cameraInsetLeftDp, cameraInsetTopDp, cameraInsetRightDp, cameraInsetBottomDp)
    )

    // 首页边界约束 + 缩放支点。
    // 约束目标：任何缩放级别下，可视窗口（黑框）始终不越过 HOME_BOUNDS + MARGIN（红框），
    // 且拖到边界时直接拖不动（零过冲"撞墙"）。
    //
    // 机制（两层）：
    // 1. 主防线 = native 中心约束。原生拖动(moveBy)/惯性/锚点缩放(moveLatLng)路径每次
    //    应用相机时都会用 LatLngBounds 同步 constrain 相机中心（setLatLngZoom 内）——
    //    中心越不过去，视口自然拖不出红框。我们把边界设为"当前缩放下可取的中心范围"
    //    （allowedHomeCenterRange），zoom 变化时刷新。
    //    关键：必须用新鲜 zoom 刷新边界——双指缩放会连续改变 zoom，而 Kotlin 缓存的
    //    cameraPosition.zoom 手势期间不刷新（滞后会把中心卡离锚点轨迹、松手后回跳）。
    //    map.zoom（MapLibreMap.getZoom）直通 native，与投影矩阵无关，永远新鲜。
    //    （曾尝试用 projection 矩阵反推 zoom：启动/转场瞬态下矩阵与相机不一致，会
    //    推出垃圾值。已放弃。）
    // 2. 兜底 = idle 时（此时缓存已刷新）用同一套数学 jumpTo 修正残余越界。
    //
    // 事件语义（core Transform::startTransition）：拖动等即时手势每帧发 onCameraDidChange；
    // 动画过渡逐帧发 onCameraIsChanging、结束发 onCameraDidChange。
    // setLatLngBoundsForCameraTarget / moveCamera 会同步再触发相机事件，
    // 重入不加守卫会无限递归（实测 StackOverflow），全部经 cameraEventGuard 挡住。
    // native 边界状态：值为设置时的 zoom，null 表示未设置。用普通引用而非 Compose
    // state——相机回调里每帧读写，不能触发重组。
    val nativeCenterBoundsZoom = remember { DoubleRef() }
    val cameraEventGuard = remember { BooleanRef() }

    fun homeClampActive(): Boolean =
        transitionProgressRef.value < 0.05f && !isMovingToHome && !isMovingToRoute &&
            mapView.width > 0 && mapView.height > 0

    // 主防线：按当前缩放刷新 native 中心约束；离开首页或瞬态退化时清除。
    fun syncNativeCenterBounds(map: MapLibreMap, zoom: Double) {
        val w = mapView.width
        val h = mapView.height
        val density = context.resources.displayMetrics.density
        val usable = homeClampActive() && viewportFitsInHomeRegion(zoom, w, h, density)
        if (!usable) {
            if (nativeCenterBoundsZoom.value != null) {
                // 先更新标记再调 native：native 会同步触发相机事件重入
                nativeCenterBoundsZoom.value = null
                map.setLatLngBoundsForCameraTarget(null)
            }
            return
        }
        val last = nativeCenterBoundsZoom.value
        if (last == null || kotlin.math.abs(zoom - last) > 0.01) {
            val insets = insetsRef.value
            val (latRange, lngRange) = allowedHomeCenterRange(
                zoom, w, h,
                insets[0] * density, insets[1] * density, insets[2] * density, insets[3] * density,
                density,
            )
            // 先更新标记再调 native：native 会同步触发相机事件重入
            nativeCenterBoundsZoom.value = zoom
            map.setLatLngBoundsForCameraTarget(
                LatLngBounds.from(latRange.second, lngRange.second, latRange.first, lngRange.first)
            )
        }
    }

    // 兜底：相机静止后（缓存新鲜）修正残余越界。退化瞬态交给 minZoom，不干预。
    fun clampViewportToHomeBounds(map: MapLibreMap) {
        if (!homeClampActive()) return
        val p = map.cameraPosition
        val t = p.target ?: return
        val density = context.resources.displayMetrics.density
        if (!viewportFitsInHomeRegion(p.zoom, mapView.width, mapView.height, density)) return
        val insets = insetsRef.value
        val (latRange, lngRange) = allowedHomeCenterRange(
            p.zoom, mapView.width, mapView.height,
            insets[0] * density, insets[1] * density, insets[2] * density, insets[3] * density,
            density,
        )
        val lat = t.latitude.coerceIn(latRange.first, latRange.second)
        val lng = t.longitude.coerceIn(lngRange.first, lngRange.second)
        if (kotlin.math.abs(lat - t.latitude) > 1e-9 || kotlin.math.abs(lng - t.longitude) > 1e-9) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), p.zoom))
        }
    }

    DisposableEffect(mapInstance) {
        val map = mapInstance
        if (map == null) {
            onDispose { }
        } else {
            var lastFocal: PointF? = null
            var focalCleared = true

            // 缩放支点持续跟踪"我的位置"标点的屏幕位置（双指/双击/按钮缩放都围绕它）；
            // 标点出屏时恢复默认锚点（双指中点）。
            fun updateZoomPivot() {
                var target: PointF? = null
                if (homeClampActive()) {
                    val userPos = locationMarkerRef.value ?: initialPositionRef.value
                    val dot = map.projection.toScreenLocation(userPos)
                    // 相机变化瞬态投影矩阵可能给出离谱读数：明显越界的保持现有支点不动
                    val plausible = dot.x >= -mapView.width * 2f && dot.x <= mapView.width * 3f &&
                        dot.y >= -mapView.height * 2f && dot.y <= mapView.height * 3f
                    if (!plausible) return
                    if (dot.x >= 0f && dot.x <= mapView.width && dot.y >= 0f && dot.y <= mapView.height) {
                        target = dot
                    }
                }
                if (target != null) {
                    val prev = lastFocal
                    if (prev == null || kotlin.math.abs(prev.x - target.x) > 0.5f ||
                        kotlin.math.abs(prev.y - target.y) > 0.5f
                    ) {
                        lastFocal = PointF(target.x, target.y)
                        focalCleared = false
                        map.uiSettings.setFocalPoint(lastFocal)
                    }
                } else if (!focalCleared) {
                    focalCleared = true
                    lastFocal = null
                    map.uiSettings.setFocalPoint(null)
                }
            }

            // 双指缩放/动画过渡逐帧（animated transition 发 isChanging）：刷新边界 + 支点
            val changingListener = MapView.OnCameraIsChangingListener {
                if (!cameraEventGuard.value) {
                    cameraEventGuard.value = true
                    try {
                        syncNativeCenterBounds(map, map.zoom)
                        updateZoomPivot()
                    } finally {
                        cameraEventGuard.value = false
                    }
                }
            }
            // 拖动逐帧（immediate transition 只发 didChange）：刷新边界 + 支点
            val didChangeListener = MapView.OnCameraDidChangeListener {
                if (!cameraEventGuard.value) {
                    cameraEventGuard.value = true
                    try {
                        syncNativeCenterBounds(map, map.zoom)
                        updateZoomPivot()
                    } finally {
                        cameraEventGuard.value = false
                    }
                }
            }
            // 相机静止（缓存已刷新）：刷新边界 + 支点 + 修正残余越界
            val idleListener = MapLibreMap.OnCameraIdleListener {
                if (!cameraEventGuard.value) {
                    cameraEventGuard.value = true
                    try {
                        syncNativeCenterBounds(map, map.zoom)
                        updateZoomPivot()
                        clampViewportToHomeBounds(map)
                    } finally {
                        cameraEventGuard.value = false
                    }
                }
            }
            mapView.addOnCameraIsChangingListener(changingListener)
            mapView.addOnCameraDidChangeListener(didChangeListener)
            map.addOnCameraIdleListener(idleListener)
            onDispose {
                mapView.removeOnCameraIsChangingListener(changingListener)
                mapView.removeOnCameraDidChangeListener(didChangeListener)
                map.removeOnCameraIdleListener(idleListener)
            }
        }
    }

    // 原生中心约束的生命周期：离开首页时在路线相机动画开始前清除（本 effect 声明在
    // 主相机 effect 之前，同一帧重组里先执行），否则旧边界会把动画路径上的相机中心卡住；
    // 回到首页时立即按当前缩放布防，不等下一次手势。
    // 键里包含转场标志位：从路线返回时 isMovingToHome 复位会重新触发本 effect 立即布防，
    // 不留"动画结束到首次手势之间"的无约束窗口。
    LaunchedEffect(mapInstance, transitionProgress, isMovingToHome, isMovingToRoute) {
        val map = mapInstance ?: return@LaunchedEffect
        syncNativeCenterBounds(map, map.zoom)
    }

    // 首页框选需要视图尺寸；布局完成前为 0，等到有尺寸后再放行
    var mapSizeReady by remember { mutableStateOf(false) }
    LaunchedEffect(mapInstance) {
        if (mapInstance == null || mapSizeReady) return@LaunchedEffect
        while (mapView.width == 0 || mapView.height == 0) {
            delay(50)
        }
        mapSizeReady = true
    }

    // 2. 核心相机指挥部：进入/退出路线或专注时各自只触发一次平滑动画
    LaunchedEffect(
        mapInstance,
        mapSizeReady,
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
        if (!mapSizeReady) return@LaunchedEffect
        val currentTime = System.currentTimeMillis()
        // 夹取后，返回动画会平滑地上下左右平移到最终停靠位置。
        val currentMapPos = map.cameraPosition

        // 首页相机：尽量居中"我的位置"标点；可视范围不许越过 HOME_BOUNDS + MARGIN，
        // 当前缩放放不下时定位点被夹到允许范围边缘（卡边界前提下尽量居中）。
        // 首页最小缩放 = 边界+边距刚好铺满屏幕的级别（取 maxOf：屏幕 ⊆ 边界，
        // 缩到最小时看不到边界外的空白；若取 minOf 则是整框可见，视口会露出界外）。
        // 注意 MapLibre 相机世界宽度是 512 * 2^z（512px 瓦片），且相机空间单位是 dp
        // （物理 px / density），所以换算到物理像素要乘 512 * density——不是 256。
        val density = context.resources.displayMetrics.density
        val homeBounds = homeCameraBoundsWithMargin()
        val boundsWidthPx = homeBounds.longitudeSpan / 360.0 * 512.0 * density
        val boundsHeightPx = (mercatorY(homeBounds.latitudeSouth) - mercatorY(homeBounds.latitudeNorth)) * 512.0 * density
        val frameZoom = maxOf(
            kotlin.math.log2(mapView.width / boundsWidthPx),
            kotlin.math.log2(mapView.height / boundsHeightPx),
        )
        val homeZoom = 8.0
        val (latRange, lngRange) = allowedHomeCenterRange(
            homeZoom, mapView.width, mapView.height,
            cameraInsetLeftDp * density, cameraInsetTopDp * density,
            cameraInsetRightDp * density, cameraInsetBottomDp * density,
            density,
        )
        val homeTarget = LatLng(
            initialPosition.latitude.coerceIn(latRange.first, latRange.second),
            initialPosition.longitude.coerceIn(lngRange.first, lngRange.second),
        )

        // 首页时把最小缩放锁在"边界填满屏幕"的级别；进入路线/专注时恢复参数值，
        // 长路线可能需要比首页框选更小的缩放才能完整显示。
        // 同时不低于底图瓦片的最小级别（矢量瓦片 z4 起），否则缩到最小会看到空白。
        val desiredMinZoom = if (transitionProgress <= 0f) maxOf(frameZoom, BASEMAP_MIN_ZOOM) else minZoom
        if (appliedHomeMinZoom != desiredMinZoom) {
            appliedHomeMinZoom = desiredMinZoom
            map.setMinZoomPreference(desiredMinZoom)
        }

        // 核心相机指挥部：进入/退出路线或专注时各自只触发一次平滑动画
        if (transitionProgress <= 0f) {
            val dist = homeTarget.distanceTo(currentMapPos.target ?: homeTarget)
            val zoomDiff = kotlin.math.abs(homeZoom - currentMapPos.zoom)

            if (dist > 1.0 || zoomDiff > 0.01) {
                // 如果是从路线退出，或者当前不在首页位置，执行平滑动画
                if (wasRouteActive) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(homeTarget, homeZoom), 500)
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(homeTarget, homeZoom))
                }
            }
            lastAnimatedPosition = homeTarget
            lastAnimatedZoom = homeZoom
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

            map.easeCamera(CameraUpdateFactory.newLatLngZoom(homeTarget, homeZoom), 500)
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

    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.matchParentSize(),
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
}

/** 首页相机目标的可行范围（大致覆盖中国） */
// 车站网包围盒（18.2N–49.6N, 87.3E–131.2E）加少量边距。
// 必须包含用户所在位置（如双鸭山 46.6N），否则相机中心被夹在 41N，
// 放大后用户位置将永远无法进入视野。
private const val HOME_BOUNDS_NORTH = 50.5
private const val HOME_BOUNDS_EAST = 132.5
private const val HOME_BOUNDS_SOUTH = 18.0
private const val HOME_BOUNDS_WEST = 87.0

/** 边界外允许流出的一小条地图（拖到边缘时仍可见） */
private const val HOME_BOUNDS_MARGIN = 2.0

/** 矢量底图瓦片的最小级别（见 createMinimalOSMStyle 的 source minzoom），低于它没有内容可渲染 */
private const val BASEMAP_MIN_ZOOM = 4.0

/** 可变的 Double? 引用：在相机回调里记录状态而不触发 Compose 重组 */
private class DoubleRef(var value: Double? = null)

/** 可变的 Boolean 引用：相机事件重入守卫，不触发 Compose 重组 */
private class BooleanRef(var value: Boolean = false)

/** 指定缩放下区域（HOME_BOUNDS + MARGIN）能否完整覆盖屏幕；不能则约束退化，不应设边界 */
private fun viewportFitsInHomeRegion(zoom: Double, viewWidthPx: Int, viewHeightPx: Int, density: Float): Boolean {
    if (viewWidthPx <= 0 || viewHeightPx <= 0) return false
    val worldPx = 512.0 * Math.pow(2.0, zoom) * density
    val regionWidthPx =
        (HOME_BOUNDS_EAST + HOME_BOUNDS_MARGIN - (HOME_BOUNDS_WEST - HOME_BOUNDS_MARGIN)) / 360.0 * worldPx
    val regionHeightPx =
        (mercatorY(HOME_BOUNDS_SOUTH - HOME_BOUNDS_MARGIN) - mercatorY(HOME_BOUNDS_NORTH + HOME_BOUNDS_MARGIN)) * worldPx
    return regionWidthPx >= viewWidthPx && regionHeightPx >= viewHeightPx
}

private fun homeCameraBoundsWithMargin(): LatLngBounds = LatLngBounds.from(
    HOME_BOUNDS_NORTH + HOME_BOUNDS_MARGIN, HOME_BOUNDS_EAST + HOME_BOUNDS_MARGIN,
    HOME_BOUNDS_SOUTH - HOME_BOUNDS_MARGIN, HOME_BOUNDS_WEST - HOME_BOUNDS_MARGIN,
)

/** Web Mercator Y 坐标（0-1，北小南大） */
private fun mercatorY(lat: Double): Double {
    val rad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0
}

/** Web Mercator Y 反解纬度 */
private fun invMercatorY(y: Double): Double {
    val t = Math.PI * (1.0 - 2.0 * y.coerceIn(0.0, 1.0))
    return Math.toDegrees(Math.atan(Math.sinh(t)))
}

/**
 * 指定缩放级别下相机中心的可取范围：约束可视窗口不越过 HOME_BOUNDS + MARGIN。
 * content padding 不对称时相机目标投影点偏离屏幕中心，视口四边到投影点的距离
 * 分开计算（如首页底部面板使投影点偏上，南侧可见范围更大）。
 * 某维度屏幕大于框（范围反转）时退化为框中心。
 * 世界宽度 = 512 * 2^zoom * density（MapLibre 相机空间是 512px 瓦片、dp 单位）。
 */
private fun allowedHomeCenterRange(
    zoom: Double,
    viewWidthPx: Int,
    viewHeightPx: Int,
    padLeftPx: Float,
    padTopPx: Float,
    padRightPx: Float,
    padBottomPx: Float,
    density: Float,
): Pair<Pair<Double, Double>, Pair<Double, Double>> {
    val worldPx = 512.0 * Math.pow(2.0, zoom) * density
    // 视口各边缘到相机目标投影点的距离（投影点位于 padding 后的中心）
    val westPx = (viewWidthPx + padLeftPx - padRightPx) / 2.0
    val eastPx = (viewWidthPx - padLeftPx + padRightPx) / 2.0
    val northPx = (viewHeightPx + padTopPx - padBottomPx) / 2.0
    val southPx = (viewHeightPx - padTopPx + padBottomPx) / 2.0
    var minLat = invMercatorY(mercatorY(HOME_BOUNDS_SOUTH - HOME_BOUNDS_MARGIN) - southPx / worldPx)
    var maxLat = invMercatorY(mercatorY(HOME_BOUNDS_NORTH + HOME_BOUNDS_MARGIN) + northPx / worldPx)
    if (minLat > maxLat) {
        val c = (HOME_BOUNDS_SOUTH + HOME_BOUNDS_NORTH) / 2.0
        minLat = c; maxLat = c
    }
    var minLng = HOME_BOUNDS_WEST - HOME_BOUNDS_MARGIN + westPx * 360.0 / worldPx
    var maxLng = HOME_BOUNDS_EAST + HOME_BOUNDS_MARGIN - eastPx * 360.0 / worldPx
    if (minLng > maxLng) {
        val c = (HOME_BOUNDS_WEST + HOME_BOUNDS_EAST) / 2.0
        minLng = c; maxLng = c
    }
    return (minLat to maxLat) to (minLng to maxLng)
}

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
