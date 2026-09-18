package com.hsr.railfocus.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.hsr.railfocus.data.preferences.DailyGoalState
import com.hsr.railfocus.R
import com.hsr.railfocus.ui.history.HistoryUiState
import com.hsr.railfocus.ui.history.HistoryViewModel
import com.hsr.railfocus.util.ProvinceFormatter
import java.text.NumberFormat
import java.util.Locale

/**
 * 数据 内容面板
 *
 * 展示各种有趣的专注旅程数据，包含图表、趣味对比和统计。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataContent(
    uiState: HistoryUiState,
    dailyGoal: DailyGoalState = DailyGoalState(45, 0, 0),
    onGoalSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stats = remember(uiState.tickets) {
        calculateDataStats(uiState.tickets.map { it.record })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.data_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        DailyGoalCard(
            dailyGoal = dailyGoal,
            onGoalSelected = onGoalSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        TotalDistanceCard(
            totalDistanceKm = stats.totalDistanceKm,
            totalJourneys = stats.totalJourneys,
            averageFocusMinutes = stats.averageFocusMinutes,
        )

        WeeklyFocusChart(
            data = stats.weeklyFocus,
            modifier = Modifier.fillMaxWidth(),
        )

        FunFactsGrid(
            equatorLoops = stats.equatorLoops,
            chinaCrossings = stats.chinaCrossings,
            averageFocusMinutes = stats.averageFocusMinutes,
            totalJourneys = stats.totalJourneys,
            modifier = Modifier.fillMaxWidth(),
        )

        ProvincesSection(
            litProvinces = stats.visitedProvinces,
            modifier = Modifier.fillMaxWidth(),
        )

        MilestonesSection(
            totalDistanceKm = stats.totalDistanceKm,
            modifier = Modifier.fillMaxWidth(),
        )

        TopDestinationsSection(
            destinations = stats.topDestinations,
            modifier = Modifier.fillMaxWidth(),
        )

        StatsGrid(
            stats = stats,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 数据 底部弹出面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBottomSheet(
    onDismiss: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dailyGoal by viewModel.dailyGoalState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
        },
    ) {
        DataContent(
            uiState = uiState,
            dailyGoal = dailyGoal,
            onGoalSelected = viewModel::setDailyGoal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TotalDistanceCard(
    totalDistanceKm: Double,
    totalJourneys: Int,
    averageFocusMinutes: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_bullet_train),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(10.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                )
                Text(
                    text = stringResource(R.string.data_total_distance),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = NumberFormat.getNumberInstance(Locale.CHINA).format(totalDistanceKm.toLong()) + " " + stringResource(R.string.history_unit_km),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.data_journey_summary, totalJourneys, averageFocusMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun DailyGoalCard(
    dailyGoal: DailyGoalState,
    onGoalSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = (dailyGoal.todayFocusMin.toFloat() / dailyGoal.goalMin).coerceIn(0f, 1f)
    val goalAchieved = dailyGoal.todayFocusMin >= dailyGoal.goalMin

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 环形进度
            Box(
                modifier = Modifier.size(84.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ringColor = if (goalAchieved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                }
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dailyGoal.todayFocusMin.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = if (goalAchieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "/ " + dailyGoal.goalMin.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (goalAchieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (goalAchieved) {
                            stringResource(R.string.data_daily_goal_done)
                        } else {
                            stringResource(R.string.data_daily_goal)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = stringResource(R.string.data_streak_days, dailyGoal.streakDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 目标档位
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(30, 45, 60, 90).forEach { goal ->
                        val selected = goal == dailyGoal.goalMin
                        Surface(
                            onClick = { onGoalSelected(goal) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Text(
                                text = goal.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyFocusChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.data_recent_focus),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.data_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 16.dp),
                ) {
                    val maxMinutes = data.maxOfOrNull { it.second } ?: 1
                    val barColor = MaterialTheme.colorScheme.primary

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val barWidth = width / (data.size * 2)
                        val spacing = width / data.size

                        // 绘制背景渐变
                        val points = data.mapIndexed { index, (_, value) ->
                            val x = index * spacing + spacing / 2
                            val y = height - (value.toFloat() / maxMinutes * height * 0.8f)
                            Offset(x, y)
                        }

                        if (points.size >= 2) {
                            val areaPath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                                lineTo(points.last().x, height)
                                lineTo(points.first().x, height)
                                close()
                            }

                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        barColor.copy(alpha = 0.3f),
                                        barColor.copy(alpha = 0.05f)
                                    ),
                                    startY = 0f,
                                    endY = height
                                )
                            )

                            // 绘制平滑曲线
                            drawPath(
                                path = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    points.drop(1).forEach { lineTo(it.x, it.y) }
                                },
                                color = barColor,
                                style = Stroke(width = 4.dp.toPx()),
                                alpha = 0.8f
                            )

                            // 绘制数据点
                            points.forEach { point ->
                                drawCircle(
                                    color = Color.White,
                                    radius = 4.dp.toPx(),
                                    center = point
                                )
                                drawCircle(
                                    color = barColor,
                                    radius = 4.dp.toPx(),
                                    center = point,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }

                    // 绘制 X 轴标签
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        data.forEach { (label, _) ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FunFactsGrid(
    equatorLoops: Double,
    chinaCrossings: Double,
    averageFocusMinutes: Int,
    totalJourneys: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(text = stringResource(R.string.data_fun_facts_header))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FunFactCard(
                icon = Icons.Default.Explore,
                label = stringResource(R.string.data_fact_equator),
                value = String.format(Locale.CHINA, "%.2f", equatorLoops),
                unit = stringResource(R.string.data_unit_loops),
                modifier = Modifier.weight(1f),
            )
            FunFactCard(
                icon = Icons.Default.Route,
                label = stringResource(R.string.data_fact_china),
                value = String.format(Locale.CHINA, "%.2f", chinaCrossings),
                unit = stringResource(R.string.data_unit_times),
                modifier = Modifier.weight(1f),
            )
            FunFactCard(
                icon = Icons.Default.AccessTime,
                label = stringResource(R.string.data_fact_avg_focus),
                value = averageFocusMinutes.toString(),
                unit = stringResource(R.string.data_unit_minutes),
                modifier = Modifier.weight(1f),
            )
            FunFactCard(
                icon = Icons.Default.EmojiEvents,
                label = stringResource(R.string.data_fact_completed),
                value = totalJourneys.toString(),
                unit = stringResource(R.string.data_unit_times),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TopDestinationsSection(
    destinations: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(text = stringResource(R.string.data_top_destinations))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                destinations.forEachIndexed { index, (city, count) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (index + 1).toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Text(
                                    text = city,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = stringResource(R.string.data_arrival_count, count),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        val maxCount = destinations.firstOrNull()?.second ?: 1
                        LinearProgressIndicator(
                            progress = { count.toFloat() / maxCount },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 34 个省级行政区，用于"点亮省份"展示 */
private val ALL_PROVINCES = listOf(
    "\u5317\u4eac\u5e02", "\u5929\u6d25\u5e02", "\u6cb3\u5317\u7701", "\u5c71\u897f\u7701",
    "\u5185\u8499\u53e4\u81ea\u6cbb\u533a", "\u8fbd\u5b81\u7701", "\u5409\u6797\u7701",
    "\u9ed1\u9f99\u6c5f\u7701", "\u4e0a\u6d77\u5e02", "\u6c5f\u82cf\u7701", "\u6d59\u6c5f\u7701",
    "\u5b89\u5fbd\u7701", "\u798f\u5efa\u7701", "\u6c5f\u897f\u7701", "\u5c71\u4e1c\u7701",
    "\u6cb3\u5357\u7701", "\u6e56\u5317\u7701", "\u6e56\u5357\u7701", "\u5e7f\u4e1c\u7701",
    "\u5e7f\u897f\u58ee\u65cf\u81ea\u6cbb\u533a", "\u6d77\u5357\u7701", "\u91cd\u5e86\u5e02",
    "\u56db\u5ddd\u7701", "\u8d35\u5dde\u7701", "\u4e91\u5357\u7701", "\u897f\u85cf\u81ea\u6cbb\u533a",
    "\u9655\u897f\u7701", "\u7518\u8083\u7701", "\u9752\u6d77\u7701", "\u5b81\u590f\u56de\u65cf\u81ea\u6cbb\u533a",
    "\u65b0\u7586\u7ef4\u543e\u5c14\u81ea\u6cbb\u533a", "\u9999\u6e2f\u7279\u522b\u884c\u653f\u533a",
    "\u6fb3\u95e8\u7279\u522b\u884c\u653f\u533a", "\u53f0\u6e7e\u7701",
)

@Composable
private fun ProvincesSection(
    litProvinces: List<String>,
    modifier: Modifier = Modifier,
) {
    val lit = litProvinces.toSet()
    Column(modifier = modifier) {
        SectionTitle(text = stringResource(R.string.data_provinces_title, lit.size, ALL_PROVINCES.size))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ALL_PROVINCES.forEach { province ->
                    val isLit = lit.contains(province)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isLit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                    ) {
                        Text(
                            text = province,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLit) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestonesSection(
    totalDistanceKm: Double,
    modifier: Modifier = Modifier,
) {
    val milestones = listOf(1000.0, 5000.0, 10000.0, 40075.0)
    Column(modifier = modifier) {
        SectionTitle(text = stringResource(R.string.data_milestones_title))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                milestones.forEach { threshold ->
                    val reached = totalDistanceKm >= threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (reached) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.data_milestone_km, threshold.toLong()),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (reached) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            )
                            LinearProgressIndicator(
                                progress = { (totalDistanceKm / threshold).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Text(
                            text = if (reached) {
                                stringResource(R.string.data_milestone_reached)
                            } else {
                                stringResource(R.string.data_milestone_pending)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (reached) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(
    stats: DataStats,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(text = stringResource(R.string.data_stats_grid))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = stringResource(R.string.data_stat_total_time),
                value = stats.totalFocusMinutes.toString(),
                unit = stringResource(R.string.data_unit_minutes),
                icon = Icons.Default.Schedule,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.data_stat_cities),
                value = stats.uniqueCities.toString(),
                unit = stringResource(R.string.data_unit_cities),
                icon = Icons.Default.Place,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.data_stat_max_dist),
                value = stats.longestJourneyKm.toInt().toString(),
                unit = stringResource(R.string.history_unit_km),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.data_stat_min_dist),
                value = stats.shortestJourneyKm.toInt().toString(),
                unit = stringResource(R.string.history_unit_km),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
        modifier = modifier.padding(bottom = 4.dp),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun FunFactCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                    )
                    if (unit != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

data class DataStats(
    val totalJourneys: Int,
    val totalFocusMinutes: Int,
    val totalDistanceKm: Double,
    val visitedProvinces: List<String>,
    val uniqueCities: Int,
    val longestJourneyKm: Double,
    val shortestJourneyKm: Double,
    val equatorLoops: Double,
    val chinaCrossings: Double,
    val averageFocusMinutes: Int,
    val weeklyFocus: List<Pair<String, Int>>,
    val topDestinations: List<Pair<String, Int>>,
)

private fun calculateDataStats(records: List<com.hsr.railfocus.domain.model.JourneyRecord>): DataStats {
    val totalJourneys = records.size
    val totalFocusMinutes = records.sumOf { it.actualDurationMin }
    val totalDistanceKm = records.sumOf { it.path.totalDistanceKm }
    val uniqueCities = records.map { it.endStation.city }.distinct().size
    val longestJourneyKm = records.maxOfOrNull { it.path.totalDistanceKm } ?: 0.0
    val shortestJourneyKm = records.minOfOrNull { it.path.totalDistanceKm } ?: 0.0
    val averageFocusMinutes = if (totalJourneys > 0) totalFocusMinutes / totalJourneys else 0
    val equatorLoops = if (totalDistanceKm > 0) totalDistanceKm / 40075.0 else 0.0
    val chinaCrossings = if (totalDistanceKm > 0) totalDistanceKm / 5500.0 else 0.0

    val weeklyFocus = records
        .asSequence()
        .sortedByDescending { it.completedAt ?: it.createdAt }
        .take(7)
        .toList()
        .reversed()
        .map { record ->
            val date = java.text.SimpleDateFormat("MM/dd", Locale.CHINA)
                .format(java.util.Date(record.completedAt ?: record.createdAt))
            date to record.actualDurationMin
        }

    val visitedProvinces = records
        .asSequence()
        .flatMap { it.path.path.asSequence() }
        .map { ProvinceFormatter.format(it.province) }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    val topDestinations = records
        .groupingBy { it.endStation.city }
        .eachCount()
        .toList()
        .asSequence()
        .sortedByDescending { it.second }
        .take(3)
        .toList()

    return DataStats(
        totalJourneys = totalJourneys,
        totalFocusMinutes = totalFocusMinutes,
        totalDistanceKm = totalDistanceKm,
        visitedProvinces = visitedProvinces,
        uniqueCities = uniqueCities,
        longestJourneyKm = longestJourneyKm,
        shortestJourneyKm = shortestJourneyKm,
        equatorLoops = equatorLoops,
        chinaCrossings = chinaCrossings,
        averageFocusMinutes = averageFocusMinutes,
        weeklyFocus = weeklyFocus,
        topDestinations = topDestinations,
    )
}
