package com.hsr.railfocus.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hsr.railfocus.R
import com.hsr.railfocus.ui.theme.ContextLine
import com.hsr.railfocus.ui.theme.LocalReduceMotion
import com.hsr.railfocus.ui.theme.Motion
import com.hsr.railfocus.ui.theme.RailColors
import com.hsr.railfocus.ui.theme.TimerDisplay

/**
 * 专注页 HUD：语境行 / 进度线 / 主数字 / 副信息。
 * 单焦点设计，同屏文字块 ≤ 6 个，每秒变化元素 ≤ 2 个。
 */
@Composable
fun FocusHud(
    state: FocusSessionUiState,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val isApproaching = state.overallProgress > 0.9f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 唯一主角：剩余时间。与起终点、进度使用同一条中心轴。
        // E 主信息：剩余时间（72sp，唯一视觉主角）
        val displayAlpha by animateFloatAsState(
            targetValue = if (state.isPaused) 0.4f else 1f,
            animationSpec = Motion.settle,
            label = "timer_alpha",
        )
        Text(
            text = formatMmSs(state.remainingSeconds),
            style = TimerDisplay,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = displayAlpha),
        )

        Spacer(Modifier.height(14.dp))

        // D 进度线：2dp + 光点
        ProgressRail(
            progress = state.overallProgress,
            isApproaching = isApproaching,
            reduceMotion = reduceMotion,
        )

        Spacer(Modifier.height(12.dp))

        // C 语境行：起点时间 起点站 ─ 终点站 预计到达
        ContextLineRow(state)

        Spacer(Modifier.height(6.dp))

        // F 状态信息：只在暂停或即将抵达时出现
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isPaused) {
                PausedBadge()
            }
            if (isApproaching && !state.isPaused) {
                Text(
                    text = stringResource(R.string.focus_approaching, state.endStation.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = RailColors.SignalYellow,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ContextLineRow(state: FocusSessionUiState) {
    // 每分钟更新一次；不要在 Compose 中通过系统时间造成秒级重组
    val startTime = rememberStartTime(state)
    val endTime = rememberEndTime(state)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isDwelling) {
            // 停靠时替换为停靠信息
            Text(
                text = stringResource(R.string.focus_dwelling_context, state.currentStation?.displayName ?: ""),
                style = ContextLine,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "$startTime ${state.startStation.displayName}",
                style = ContextLine,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.endStation.displayName} $endTime",
                style = ContextLine,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgressRail(
    progress: Float,
    isApproaching: Boolean,
    reduceMotion: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val dotColor = if (isApproaching) RailColors.SignalYellow else primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .padding(vertical = 0.dp)
    ) {
        val width = size.width
        val progressWidth = width * progress.coerceIn(0f, 1f)

        // 未走部分
        drawLine(
            color = trackColor,
            start = Offset(0f, 0f),
            end = Offset(width, 0f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // 已走部分
        if (progressWidth > 0) {
            drawLine(
                color = primary,
                start = Offset(0f, 0f),
                end = Offset(progressWidth, 0f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            // 光点
            drawCircle(
                color = dotColor,
                radius = 4.dp.toPx(),
                center = Offset(progressWidth, 0f),
            )
        }
    }
}

@Composable
private fun PausedBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Text(
            text = stringResource(R.string.focus_paused_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

internal fun formatMmSs(seconds: Int): String {
    val m = (seconds / 60).coerceAtLeast(0)
    val s = (seconds % 60).coerceAtLeast(0)
    return "%02d:%02d".format(m, s)
}

/** 纯净模式：只保留时间与进度，不再显示站点和里程文字。 */
@Composable
fun PureFocusHud(
    remainingSeconds: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatMmSs(remainingSeconds),
            style = TimerDisplay,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))
        ProgressRail(
            progress = progress,
            isApproaching = progress > 0.9f,
            reduceMotion = LocalReduceMotion.current,
        )
    }
}

@Composable
private fun rememberStartTime(state: FocusSessionUiState): String {
    // 从 totalSeconds 和 remainingSeconds 推算开始时间
    // 简化：用当前时间减去已进行时间
    val elapsed = state.totalSeconds - state.remainingSeconds
    val startMillis = System.currentTimeMillis() - elapsed * 1000L
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

@Composable
private fun rememberEndTime(state: FocusSessionUiState): String {
    val endMillis = System.currentTimeMillis() + state.remainingSeconds * 1000L
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = endMillis }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}
