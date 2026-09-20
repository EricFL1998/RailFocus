package com.hsr.railfocus.ui.journeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hsr.railfocus.R
import com.hsr.railfocus.ui.components.MapLibreView
import com.hsr.railfocus.util.ChinaBounds
import org.maplibre.android.geometry.LatLng
import java.text.NumberFormat
import java.util.Locale

/**
 * 总旅程视图的默认缩放级别：往外缩到底图仍然铺满屏幕的最远一级。
 *
 * 打包的矢量瓦片覆盖 z0-10（见 MapLibreView 里底图 source 的 minzoom/maxzoom），
 * 但源数据只有中国范围内的要素，瓦片只覆盖到「赤道以北、东经 45° 以东」这一片。
 * z3 的瓦片正好覆盖 lat 0-66.5、lng 45-180，屏幕（约 2.2:1）能完整落在这片范围内：
 * 43.95°N 处一屏约装下 26 个经度、40 个纬度，再往外缩上下就会出现没有地图的空白带。
 */
private const val OVERVIEW_ZOOM = 3.5

/** 没有任何已完成旅程时的兜底中心：整片中国 */
private val CHINA_CENTER: LatLng = LatLng(
    (ChinaBounds.bounds.latitudeNorth + ChinaBounds.bounds.latitudeSouth) / 2,
    (ChinaBounds.bounds.longitudeEast + ChinaBounds.bounds.longitudeWest) / 2,
)

/**
 * 总旅程视图
 *
 * 全屏地图，把全部已完成旅程的线路同时画出来；进入时相机先缩到最小级别（一屏装下整片中国），
 * 并居中在全部线路的重心上，之后可以用手势自由缩放拖动。
 */
@Composable
fun AllJourneysScreen(
    onBack: () -> Unit,
    viewModel: AllJourneysViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val hasRoutes = uiState.routes.isNotEmpty()
    // 默认视野：缩到最小级别，地图居中在全部已完成线路的重心上
    val overviewCamera = (uiState.cameraCenter ?: CHINA_CENTER) to OVERVIEW_ZOOM

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            // 这里不展示“我的位置”标点（经纬度 0,0 会被地图组件忽略）
            initialPosition = LatLng(0.0, 0.0),
            initialZoom = OVERVIEW_ZOOM,
            minZoom = OVERVIEW_ZOOM,
            maxZoom = 12.0,
            // 固定为 1：地图始终按“路线视角”渲染，线路全量可见
            transitionProgress = 1f,
            stations = emptyList(),
            showStationMarkers = false,
            overviewCamera = overviewCamera,
            overlayRoutes = uiState.routes,
            enableGestures = true,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.selection_back),
                    )
                }

                Surface(
                    modifier = Modifier.weight(1f, fill = false),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text(
                            text = stringResource(R.string.all_journeys_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(
                                R.string.all_journeys_summary,
                                uiState.journeyCount,
                                NumberFormat.getNumberInstance(Locale.CHINA).format(uiState.totalDistanceKm.toLong()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (!uiState.isLoading && !hasRoutes) {
            EmptyJourneysHint(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        }
    }
}

@Composable
private fun EmptyJourneysHint(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Route,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .alpha(0.55f),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.all_journeys_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.all_journeys_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
