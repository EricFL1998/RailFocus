package com.hsr.railfocus.ui.components

import kotlinx.coroutines.launch

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.model.StationFact
import com.hsr.railfocus.domain.model.MemoryRecall
import com.hsr.railfocus.domain.model.JourneyJournal
import com.hsr.railfocus.ui.journal.CompletionJournalCard
import com.hsr.railfocus.ui.journal.MemoryRecallCard
import com.hsr.railfocus.ui.focus.*
import com.hsr.railfocus.ui.theme.RailColors

@Composable
fun CountdownDisplay(remainingSeconds: Int, modifier: Modifier = Modifier) {
    val minutes = (remainingSeconds / 60).coerceAtLeast(0)
    val seconds = (remainingSeconds % 60).coerceAtLeast(0)

    val minTens = minutes / 10
    val minOnes = minutes % 10
    val secTens = seconds / 10
    val secOnes = seconds % 10

    val infiniteTransition = rememberInfiniteTransition(label = "colon_blink")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colon_alpha"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RollingDigit(minTens)
            RollingDigit(minOnes)
            Text(
                text = ":",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = colonAlpha),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .offset(y = (-2).dp)
            )
            RollingDigit(secTens)
            RollingDigit(secOnes)
        }
    }
}

@Composable
private fun RollingDigit(digit: Int) {
    AnimatedContent(
        targetState = digit,
        transitionSpec = {
            if (targetState < initialState) {
                (slideInVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { -it / 2 } + fadeIn(tween(180)))
                    .togetherWith(slideOutVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it / 2 } + fadeOut(tween(140)))
            } else {
                (slideInVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(180)))
                    .togetherWith(slideOutVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(140)))
            }
        },
        label = "digit_slide"
    ) { d ->
        Text(
            text = d.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFeatureSettings = "tnum",
                letterSpacing = (-1).sp
            ),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun StationInfoCard(
    currentStation: Station?,
    nextStation: Station?,
    speed: Float,
    isDwelling: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        AnimatedContent(
            targetState = isDwelling,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(220)))
                    .togetherWith(slideOutVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(180)))
            },
            label = "station_card_dwell_anim"
        ) { dwelling ->
            if (dwelling) {
                // 已到站停靠面板
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1.4f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = stringResource(R.string.focus_dwelling_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = currentStation?.name ?: "--",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, stepSize = 2.sp)
                        )
                    }

                    // 中间：前进路线指示图标（到站停靠面板不显示速度）
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )

                    // 右侧：下一站（弱化，非核心）
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.focus_next_station_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = nextStation?.name ?: "--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    StationLabel(
                        title = stringResource(R.string.focus_current_station),
                        name = currentStation?.name ?: "--",
                        modifier = Modifier.weight(1f)
                    )

                    // 速度块不参与权重分配，按内容取宽；左右两站再平分剩余空间。
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AnimatedContent(
                                targetState = speed.toInt(),
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInVertically { it / 2 } + fadeIn(tween(140))).togetherWith(
                                            slideOutVertically { -it / 2 } + fadeOut(tween(140))
                                        )
                                    } else {
                                        (slideInVertically { -it / 2 } + fadeIn(tween(140))).togetherWith(
                                            slideOutVertically { it / 2 } + fadeOut(tween(140))
                                        )
                                    }
                                },
                                label = "speed_anim"
                            ) { speedInt ->
                                Text(
                                    text = speedInt.toString(),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(84.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = stringResource(R.string.focus_speed_unit),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    StationLabel(
                        title = stringResource(R.string.focus_next_station),
                        name = nextStation?.name ?: "--",
                        modifier = Modifier.weight(1f),
                        alignRight = true
                    )
                }
            }
        }
    }
}

@Composable
fun StationLabel(
    title: String,
    name: String,
    modifier: Modifier = Modifier,
    alignRight: Boolean = false
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0) }
    Column(
        modifier = modifier.onSizeChanged { widthPx = it.width },
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        // 以三字站名为基准字号：更少的字不放大，更多的字才缩小；宽度固定后不再跳动
        val referenceChars = name.length.coerceAtLeast(3)
        val defaultSize = MaterialTheme.typography.headlineSmall.fontSize
        val nameSize = if (widthPx > 0) {
            val widthDp = with(density) { widthPx.toDp() }
            val perChar = widthDp / referenceChars
            val fit = with(density) { perChar.toSp() } * 0.94f
            if (fit.value < defaultSize.value) fit else defaultSize
        } else {
            defaultSize
        }
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = nameSize),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * 高铁车厢经由路线示意条 (Route Progression Strip)
 *
 * 模仿中国高铁车厢门上方的动态站点全景示意条：
 * - 实时显示整条专注旅程的所有停靠站/经由站；
 * - 已通过车站标记为实心绿色对勾；
 * - 当前区间/正在停靠车站有迷你高铁动车组图标与动态脉冲指示；
 * - 即将到达的下一站与终点站清晰可见，赋予用户强烈的空间前行感。
 */
@Composable
fun RouteProgressionStrip(
    stations: List<Station>,
    currentStation: Station?,
    nextStation: Station?,
    overallProgress: Float,
    isDwelling: Boolean,
    modifier: Modifier = Modifier
) {
    if (stations.size < 2) return

    val currentStationIndex = remember(stations, currentStation) {
        val idx = stations.indexOfFirst { it.id == currentStation?.id }
        if (idx >= 0) idx else 0
    }
    val progressClamped = overallProgress.coerceIn(0f, 1f)
    val scrollState = rememberScrollState()

    // 当站点较多时，自动随进度平滑滚动居中当前车站
    LaunchedEffect(currentStationIndex) {
        if (stations.size > 5) {
            val targetScroll = (scrollState.maxValue * (currentStationIndex.toFloat() / (stations.size - 1))).toInt()
            scrollState.animateScrollTo(targetScroll)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 头部标题与进度百分比
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BulletTrainIcon(
                        size = 15.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.focus_route_progress_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isDwelling) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.focus_dwelling_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${(progressClamped * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // 站点轨道示意图
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_train")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_train_alpha"
            )

            val isCompact = stations.size <= 5
            val contentModifier = if (isCompact) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            }

            Row(
                modifier = contentModifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isCompact) Arrangement.SpaceBetween else Arrangement.spacedBy(0.dp)
            ) {
                stations.forEachIndexed { index, station ->
                    val isEnd = index == stations.size - 1
                    val isPassed = index < currentStationIndex
                    val isCurrent = index == currentStationIndex

                    val itemWidth = if (isCompact) Modifier.weight(1f, fill = false) else Modifier.widthIn(min = 68.dp)

                    Column(
                        modifier = itemWidth,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 节点图标
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(22.dp)
                        ) {
                            when {
                                isPassed -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                                isCurrent -> {
                                    if (isDwelling) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.35f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            ) {}
                                        }
                                    } else {
                                        BulletTrainIcon(
                                            size = 20.dp,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                else -> {
                                    Surface(
                                        shape = CircleShape,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(12.dp)
                                    ) {}
                                }
                            }
                        }

                        // 站名
                        Text(
                            text = station.name,
                            style = if (isCurrent) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isPassed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    // 站与站之间的连线
                    if (!isEnd) {
                        val isSegmentPassed = index < currentStationIndex
                        val isSegmentCurrent = index == currentStationIndex
                        val lineColor = when {
                            isSegmentPassed -> MaterialTheme.colorScheme.primary
                            isSegmentCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        }

                        val lineModifier = if (isCompact) {
                            Modifier
                                .weight(1f)
                                .height(2.5.dp)
                                .padding(horizontal = 2.dp)
                                .offset(y = (-11).dp)
                                .background(lineColor, RoundedCornerShape(1.dp))
                        } else {
                            Modifier
                                .width(32.dp)
                                .height(2.5.dp)
                                .offset(y = (-11).dp)
                                .background(lineColor, RoundedCornerShape(1.dp))
                        }

                        Box(modifier = lineModifier)
                    }
                }
            }
        }
    }
}

/**
 * 车厢电子内屏状态条 (Carriage Status HUD)
 *
 * 仿中国高铁车厢两端数字显示屏：
 * 呈现车型车次编号、车厢席位、实时运行状态，以及快捷车厢环境音切换按钮。
 */
@Composable
fun CarriageStatusHud(
    trainSeriesName: String,
    trainNumber: String,
    carriageNumber: String?,
    seatNumber: String?,
    focusType: FocusType?,
    speed: Float,
    isDwelling: Boolean,
    isPaused: Boolean,
    isAmbientEnabled: Boolean,
    onToggleAmbient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：列车车次与车厢信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$trainSeriesName $trainNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                val seatClean = seatNumber?.replace("号", "")?.replace(Regex("^[0-9]+车"), "")
                val seatDisplay = if (carriageNumber != null && seatClean != null) {
                    "${carriageNumber}车 $seatClean"
                } else if (carriageNumber != null) {
                    "${carriageNumber}车"
                } else null

                seatDisplay?.let { seat ->
                    Text(
                        text = seat,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                focusType?.let { ft ->
                    Text(
                        text = "· ${ft.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // 右侧：车厢白噪音快捷控制微胶囊
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleAmbient()
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isAmbientEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                },
                contentColor = if (isAmbientEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isAmbientEnabled) Icons.Default.Headphones else Icons.Default.VolumeOff,
                        contentDescription = stringResource(R.string.settings_ambient_sound),
                        modifier = Modifier.size(15.dp),
                        tint = if (isAmbientEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (isAmbientEnabled) stringResource(R.string.focus_ambient_playing) else stringResource(R.string.focus_ambient_muted),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CompletionOverlay(
    startStation: String,
    endStation: String,
    city: String,
    duration: Int,
    focusType: FocusType?,
    stationFact: StationFact?,
    completed: Boolean = true,
    delayMinutes: Int = 0,
    journal: JourneyJournal? = null,
    hasJournal: Boolean = (journal != null),
    memoryRecall: MemoryRecall? = null,
    onWriteJournal: (() -> Unit)? = null,
    onBackHome: () -> Unit
) {
    val scaleAnim = remember { androidx.compose.animation.core.Animatable(0.9f) }
    val alphaAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val offsetYAnim = remember { androidx.compose.animation.core.Animatable(70f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                alphaAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                )
            }
            launch {
                offsetYAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.82f,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.82f,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RailColors.Scrim)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = (maxHeight - 64.dp))
                .graphicsLayer {
                    alpha = alphaAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationY = offsetYAnim.value
                }
        ) {
                val cardScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .verticalScroll(cardScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = if (completed) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bullet_train),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(32.dp),
                                tint = if (completed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        
                        val infiniteTransition = rememberInfiniteTransition(label = "dots_orbit")
                        val baseAngle = infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(12000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "base_angle"
                        )
                        val orbitColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val orbitRadius = 36.dp.toPx()
                            val dotRadius = 4.dp.toPx()
                            val angle = baseAngle.value
                            for (i in 0 until 6) {
                                val rad = Math.toRadians((angle + i * 60f).toDouble())
                                val x = center.x + (orbitRadius * kotlin.math.cos(rad)).toFloat()
                                val y = center.y + (orbitRadius * kotlin.math.sin(rad)).toFloat()
                                drawCircle(color = orbitColor, radius = dotRadius, center = Offset(x, y))
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (completed) {
                            Text(
                                text = stringResource(R.string.completion_welcome),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                            PlatformStationSign(
                                stationName = endStation,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Text(
                                text = city,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.completion_journey_ended),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = endStation,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = startStation,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = endStation,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val formattedDuration = if (duration >= 60) {
                                    val h = duration / 60
                                    val m = duration % 60
                                    if (m > 0) stringResource(R.string.selection_duration_hours_mins, h, m) else stringResource(R.string.selection_duration_hours, h)
                                } else {
                                    stringResource(R.string.selection_duration_minutes, duration)
                                }
                                
                                InfoItem(
                                    label = stringResource(R.string.completion_duration_label),
                                    value = formattedDuration
                                )
                                InfoItem(
                                    label = stringResource(R.string.completion_type_label),
                                    value = focusType?.displayName ?: stringResource(R.string.completion_type_default),
                                    icon = focusType?.icon
                                )
                                InfoItem(
                                    label = stringResource(R.string.completion_delay_label),
                                    value = if (delayMinutes > 0) {
                                        stringResource(R.string.completion_delayed, delayMinutes)
                                    } else {
                                        stringResource(R.string.completion_on_time)
                                    }
                                )
                            }
                        }
                    }
                        if (completed) {
                            val arrivalStampScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "arrival_stamp_scale"
                            )
                            val isDelayed = delayMinutes > 0
                            val stampColor = if (isDelayed) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            val stampText = if (isDelayed) {
                                stringResource(R.string.completion_stamp_delayed)
                            } else {
                                stringResource(R.string.completion_stamp_arrived)
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-10).dp)
                                    .graphicsLayer {
                                        scaleX = arrivalStampScale
                                        scaleY = arrivalStampScale
                                        rotationZ = -12f
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                border = BorderStroke(2.dp, stampColor),
                            ) {
                                Text(
                                    text = stampText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = stampColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-10).dp)
                                    .graphicsLayer { rotationZ = -12f },
                                shape = RoundedCornerShape(8.dp),
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                            ) {
                                Text(
                                    text = stringResource(R.string.completion_not_completed),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // 那年今日 / 车站旧忆：再次抵达时，直接展示当年/上次的手账内容
                    if (completed && memoryRecall != null) {
                        MemoryRecallCard(
                            recall = memoryRecall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (completed && stationFact != null) {
                        val categoryIcon = when (stationFact.category) {
                            "美食" -> Icons.Default.Restaurant
                            "历史" -> Icons.Default.HistoryEdu
                            "地理" -> Icons.Default.Public
                            "人文" -> Icons.Default.Palette
                            "铁路" -> Icons.Default.Train
                            "地标" -> Icons.Default.Place
                            "趣闻" -> Icons.Default.AutoAwesome
                            "风物" -> Icons.Default.LocalMall
                            "漫游" -> Icons.Default.TravelExplore
                            else -> Icons.Default.Lightbulb
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stationFact.category,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = stationFact.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (completed) {
                            if (hasJournal) {
                                OutlinedButton(
                                    onClick = { onWriteJournal?.invoke() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(25.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.completion_journal_sealed),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { onWriteJournal?.invoke() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(27.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(if (memoryRecall != null) R.string.completion_write_new_journal else R.string.completion_write_journal),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (completed) {
                            OutlinedButton(
                                onClick = onBackHome,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(27.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.completion_finish),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Button(
                                onClick = onBackHome,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.completion_back_home),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    icon: ImageVector? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 经典中国铁路站台站名牌 (国铁蓝底白字)
 */
@Composable
fun PlatformStationSign(
    stationName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF103A70), // 经典国铁站牌钴蓝
        border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "◀",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
            Text(
                text = stationName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                text = "▶",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}
