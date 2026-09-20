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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Check
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
import kotlin.math.roundToInt

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
    // 数据统计只计入完成的旅程；取消的未完成车票不参与统计
    val stats = remember(uiState.tickets) {
        calculateDataStats(uiState.tickets.filter { it.isCompleted }.map { it.record })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
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

        // 专注目标与总里程：页面顶部的两张主卡
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

        // 里程相关的趣味对比与成就，紧跟在总里程之后
        FunFactsGrid(
            equatorLoops = stats.equatorLoops,
            chinaCrossings = stats.chinaCrossings,
            averageFocusMinutes = stats.averageFocusMinutes,
            totalJourneys = stats.totalJourneys,
            modifier = Modifier.fillMaxWidth(),
        )

        MilestonesSection(
            totalDistanceKm = stats.totalDistanceKm,
            modifier = Modifier.fillMaxWidth(),
        )

        // 足迹相关：省份打卡与近期专注
        ProvincesSection(
            litProvinces = stats.visitedProvinces,
            modifier = Modifier.fillMaxWidth(),
        )

        WeeklyFocusChart(
            data = stats.weeklyFocus,
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

        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(24.dp),
        )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                }
            }

            // 目标档位：四档等宽排布，不会再换行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(30, 45, 60, 90).forEach { goal ->
                    val selected = goal == dailyGoal.goalMin
                    Surface(
                        onClick = { onGoalSelected(goal) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            text = goal.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
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
                EmptyHint(
                    text = stringResource(R.string.data_no_data),
                    icon = Icons.Default.Insights,
                    hint = stringResource(R.string.data_recent_focus_empty),
                )
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
            if (destinations.isEmpty()) {
                EmptyHint(
                    text = stringResource(R.string.data_top_destinations_empty),
                    icon = Icons.Default.Place,
                )
            } else {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val maxCount = destinations.firstOrNull()?.second ?: 1
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
                                    modifier = Modifier.weight(1f, fill = false),
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.data_arrival_count, count),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
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
}

/** 省份打卡：33 个省级行政区，按地理分区陈列（不含台湾） */
private val PROVINCE_REGIONS: List<Pair<String, List<String>>> = listOf(
    "华北" to listOf("北京市", "天津市", "河北省", "山西省", "内蒙古自治区"),
    "东北" to listOf("辽宁省", "吉林省", "黑龙江省"),
    "华东" to listOf("上海市", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省"),
    "华中" to listOf("河南省", "湖北省", "湖南省"),
    "华南" to listOf("广东省", "广西壮族自治区", "海南省", "香港特别行政区", "澳门特别行政区"),
    "西南" to listOf("重庆市", "四川省", "贵州省", "云南省", "西藏自治区"),
    "西北" to listOf("陕西省", "甘肃省", "青海省", "宁夏回族自治区", "新疆维吾尔自治区"),
)

/** 全部可点亮的省份 */
private val ALL_PROVINCES: List<String> = PROVINCE_REGIONS.flatMap { it.second }

@Composable
private fun ProvincesSection(
    litProvinces: List<String>,
    modifier: Modifier = Modifier,
) {
    val lit = litProvinces.toSet()
    val total = ALL_PROVINCES.size
    val litCount = ALL_PROVINCES.count { lit.contains(it) }
    val progress = if (total == 0) 0f else litCount.toFloat() / total

    Column(modifier = modifier) {
        SectionTitle(text = stringResource(R.string.data_provinces_title))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 打卡总进度
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.data_provinces_progress, litCount, total),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.data_provinces_percent, (progress * 100).roundToInt()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    if (litCount == 0) {
                        Text(
                            text = stringResource(R.string.data_provinces_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }

                // 分区打卡
                PROVINCE_REGIONS.forEach { (region, provinces) ->
                    val regionLit = provinces.count { lit.contains(it) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = region,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = regionLit.toString() + "/" + provinces.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (regionLit > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            provinces.forEach { province ->
                                ProvinceChip(name = province, lit = lit.contains(province))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvinceChip(
    name: String,
    lit: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (lit) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (lit) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                color = if (lit) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (reached) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                LinearProgressIndicator(
                                    progress = { (totalDistanceKm / threshold).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Text(
                                    text = if (reached) {
                                        stringResource(R.string.data_milestone_reached)
                                    } else {
                                        stringResource(R.string.data_milestone_remaining, (threshold - totalDistanceKm).roundToInt())
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
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
    }
}

@Composable
private fun StatsGrid(
    stats: DataStats,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(text = stringResource(R.string.data_stats_grid))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
        modifier = modifier.padding(bottom = 8.dp),
        textAlign = TextAlign.Start,
    )
}

/** 卡片内的空状态占位：图标 + 说明文字 */
@Composable
private fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hint: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .padding(bottom = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
            )
        }
    }
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
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
