package com.hsr.railfocus.ui.timeselection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.service.DestinationOption
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * 可达目的地横向居中滚动选择组件
 *
 * 以紧凑的方形/矩形卡片展示可达车站。行为与上方时间刻度一致：
 * - 每次滑动/停止后自动吸附到中央 item
 * - 中央 item 自动高亮为选中态
 * - 不可点击选中
 * - 外部传入的 selectedIndex 变化时同步滚动到该位置
 */
@Composable
fun DestinationList(
    destinations: List<DestinationOption>,
    selectedIndex: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onDestinationSelected: ((Int) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isLoading && destinations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (destinations.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.dest_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            DestinationSnapPicker(
                destinations = destinations,
                selectedIndex = selectedIndex,
            ) { index ->
                onDestinationSelected?.invoke(index)
            }
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
private fun DestinationSnapPicker(
    destinations: List<DestinationOption>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val boxWidth = 92.dp
    val spacing = 8.dp
    val boxWidthPx = with(density) { boxWidth.toPx() }
    val spacingPx = with(density) { spacing.toPx() }

    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val centerPadding = with(density) {
        val pageWidth = boxWidthPx + spacingPx
        ((viewportWidthPx / 2f) - (pageWidth / 2f)).coerceAtLeast(0f).toDp()
    }

    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, destinations.size - 1),
    ) { destinations.size }

    // 外部 selectedIndex 变化时同步滚动（例如时长变化后重置到 0）
    LaunchedEffect(selectedIndex, destinations.size) {
        val target = selectedIndex.coerceIn(0, destinations.size - 1)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    // 滑动到新目的地时触发触觉反馈
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
    }

    // 滚动时实时更新选中项（与 TimeDurationPicker 一致的 currentPage 模式）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .debounce(kotlin.time.Duration.parse("80ms"))
            .collectLatest { page ->
                onIndexSelected(page)
            }
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(boxWidth + spacing),
        contentPadding = PaddingValues(horizontal = centerPadding),
        beyondViewportPageCount = 5,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = 0.5f,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size -> viewportWidthPx = size.width },
    ) { page ->
        val destination = destinations[page]
        val isSelected = (page == selectedIndex)
        val pageOffset = remember {
            derivedStateOf { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction }
        }.value
        val scale = 1f - (pageOffset.absoluteValue * 0.08f).coerceIn(0f, 0.08f)
        val alpha = 1f - (pageOffset.absoluteValue * 0.35f).coerceIn(0f, 0.35f)
        DestinationBox(
            destination = destination,
            isSelected = isSelected,
            onClick = {
                if (pagerState.currentPage != page) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                }
            },
            modifier = Modifier
                .width(boxWidth)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
        )
    }
}

/**
 * 紧凑目的地方块卡片
 *
 * 透明背景、带半透明背景或选中态的圆角方块，包含车站名、时长、距离。
 * 选中态使用品牌主色填充，未选中态使用半透明表面色。
 */
@Composable
fun DestinationBox(
    destination: DestinationOption,
    isSelected: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val animatedElevation by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSelected) 4.dp else 1.dp,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "dest_elevation"
    )
    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "dest_scale"
    )

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryContentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(0.95f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = 2.dp
        ),
        border = if (isSelected) BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary
        ) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 车站名称
            Text(
                text = destination.station.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            // 时长 + 距离
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatDurationFull(destination.travelTimeMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = String.format(java.util.Locale.CHINA, "%.0f", destination.distance) + stringResource(R.string.history_unit_km),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
