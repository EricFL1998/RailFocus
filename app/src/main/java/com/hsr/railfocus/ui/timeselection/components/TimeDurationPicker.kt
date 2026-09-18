package com.hsr.railfocus.ui.timeselection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import com.hsr.railfocus.R
import androidx.compose.ui.unit.dp
import com.hsr.railfocus.ui.theme.RailFocusTheme

/**
 * 横向滚动数字刻度条时间选择器
 *
 * 每页对应 1 分钟；主刻度（每 5 分钟）标签显示在刻度下方。
 * 绿色药丸显示在顶部中央，并随滑动实时更新。
 */
@Composable
fun TimeDurationPicker(
    selectedDuration: Int?,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minDuration: Int = 15,
    maxDuration: Int = 300,
) {
    val density = LocalDensity.current
    val snapStep = 1
    val tickSpacing = 20.dp // 增加间距 (从 14dp -> 20dp)，提供更大的精细调节空间
    val tickSpacingPx = with(density) { tickSpacing.toPx() }

    fun indexToDuration(index: Int): Int = minDuration + (index * snapStep)
    fun durationToIndex(duration: Int): Int = (duration - minDuration) / snapStep
    fun isMajorTick(duration: Int): Boolean = ((duration - minDuration) % 5 == 0)

    val totalSnapCount = (((maxDuration - minDuration) / snapStep) + 1)

    val initialPage = remember { durationToIndex(selectedDuration ?: minDuration) }
    val pagerState = rememberPagerState(initialPage = initialPage) { totalSnapCount }

    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val centerPadding = with(density) {
        ((viewportWidthPx / 2f) - (tickSpacingPx / 2f)).coerceAtLeast(0f).toDp()
    }

    // 核心改进 1: 滑动中实时更新顶部药丸，但只有在“停止滑动且对齐”后才通知外部更新路线
    // 这样可以避免在快速滑动过程中频繁重新计算路径和地图对焦，提升流畅度
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val duration = indexToDuration(pagerState.currentPage)
            // 只有当滑动彻底结束，且页面稳定在整数刻度时才回调
            onDurationSelected(duration)
        }
    }

    // 核心改进 2: 滑动到新刻度时立刻触发触觉反馈，增强“刻度感”
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(pagerState.currentPage) {
        // 只要页面索引变了（无论是否在滑动中），都震动一下
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // 同步外部 selectedDuration 到 pager (主要用于初始加载)
    LaunchedEffect(selectedDuration) {
        selectedDuration?.let { duration ->
            val page = durationToIndex(duration)
            if (page != pagerState.currentPage && page in 0 until totalSnapCount) {
                // 如果当前没在手动滑动，才同步外部数据（防止循环冲突）
                if (!pagerState.isScrollInProgress) {
                    pagerState.scrollToPage(page)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .onSizeChanged { size -> viewportWidthPx = size.width },
    ) {
        // 当前选中时间药丸，使用当前可见页实时更新，滑动时即时变化
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            Text(
                text = formatDurationFull(indexToDuration(pagerState.currentPage)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // 顶部固定指示线
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .width(2.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary),
        )

        val snapFlingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = 0.3f, // 调低阈值 (从 0.5 -> 0.3)，让其在微调时更早“吸附”到目标分钟
        )

        // 刻度条：刻度在指示线下方，标签在刻度下方
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(tickSpacing),
            contentPadding = PaddingValues(horizontal = centerPadding),
            beyondViewportPageCount = 10,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 46.dp)
                .height(36.dp),
        ) { page ->
            val duration = indexToDuration(page)
            val isSelected = page == pagerState.currentPage
            val isMajor = isMajorTick(duration)
            ScaleTick(
                duration = duration,
                isSelected = isSelected,
                isMajor = isMajor,
                tickSpacing = tickSpacingPx,
            )
        }
    }
}

@Composable
private fun ScaleTick(
    duration: Int,
    isSelected: Boolean,
    isMajor: Boolean,
    tickSpacing: Float,
) {
    val tickColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .width(with(LocalDensity.current) { tickSpacing.toDp() })
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // 刻度线
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (isMajor) 18.dp else 10.dp)
                .background(tickColor),
        )

        // 主刻度标签在刻度下方
        if (isMajor) {
            Text(
                text = formatDurationShort(duration),
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 完整中文格式，用于药丸和目的地卡片。
 */
@Composable
fun formatDurationFull(minutes: Int): String {
    return when {
        minutes < 60 -> stringResource(R.string.selection_duration_minutes, minutes)
        else -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) {
                stringResource(R.string.selection_duration_hours, hours)
            } else {
                stringResource(R.string.selection_duration_hours_mins, hours, mins)
            }
        }
    }
}

/**
 * 短格式，用于刻度标签，使用数字时间格式避免截断。
 */
fun formatDurationShort(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        "%d:%02d".format(hours, mins)
    } else {
        "%d".format(mins)
    }
}

@Preview(showBackground = true)
@Composable
private fun TimeDurationPickerPreview() {
    RailFocusTheme {
        TimeDurationPicker(
            selectedDuration = 93,
            onDurationSelected = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TimeDurationPickerShortPreview() {
    RailFocusTheme {
        TimeDurationPicker(
            selectedDuration = 18,
            minDuration = 15,
            maxDuration = 40,
            onDurationSelected = {},
        )
    }
}
