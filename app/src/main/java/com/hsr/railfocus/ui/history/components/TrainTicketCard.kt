package com.hsr.railfocus.ui.history.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.ui.history.TrainTicketModel
import com.hsr.railfocus.ui.history.TrainSeries
import com.hsr.railfocus.ui.components.BulletTrainIcon
import com.hsr.railfocus.ui.theme.RailColors
import com.hsr.railfocus.ui.theme.RailFocusTheme

/**
 * 极简飞行日志/卡包风格火车票
 */
@Composable
fun TrainTicketCard(
    ticket: TrainTicketModel,
    modifier: Modifier = Modifier,
    forceDarkTheme: Boolean? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val captureLayer = rememberGraphicsLayer()
    val notchSize = 16.dp

    // 自动适配深色模式（根据主题背景明度），也可通过 forceDarkTheme 显式指定
    val isDark = forceDarkTheme ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)

    val statusColor = if (isDark) {
        if (ticket.isCompleted) Color(0xFF81C784) else Color(0xFFEF5350)
    } else {
        if (ticket.isCompleted) Color(0xFF4CAF50) else Color(0xFFF44336)
    }

    // 依据不同车型（C/D/G/动卧）定制票面质感与边框，并在深色模式下保持低饱和舒适度
    val (containerColor, cardBorder, seriesBadgeBg, seriesBadgeColor, seriesLabel) = when (ticket.trainSeries) {
        TrainSeries.C_SERIES -> {
            if (isDark) {
                tuple5(Color(0xFF13201B), BorderStroke(1.dp, Color(0xFF233830)), Color(0xFF004D40).copy(alpha = 0.45f), Color(0xFF80CBC4), "城际")
            } else {
                tuple5(Color(0xFFFBFDFB), BorderStroke(1.dp, Color(0xFFE0F2F1)), Color(0xFFE0F2F1), Color(0xFF00796B), "城际")
            }
        }
        TrainSeries.D_SERIES -> {
            if (isDark) {
                tuple5(Color(0xFF131D28), BorderStroke(1.dp, Color(0xFF223547)), Color(0xFF0D47A1).copy(alpha = 0.45f), Color(0xFF90CAF9), "和谐号")
            } else {
                tuple5(Color(0xFFF9FBFE), BorderStroke(1.dp, Color(0xFFE3F2FD)), Color(0xFFE3F2FD), Color(0xFF1976D2), "和谐号")
            }
        }
        TrainSeries.G_SERIES -> {
            if (isDark) {
                tuple5(Color(0xFF211A14), BorderStroke(1.dp, Color(0xFF3D2F22)), Color(0xFF5D4037).copy(alpha = 0.5f), Color(0xFFFFD54F), "复兴号")
            } else {
                tuple5(Color(0xFFFFFDF8), BorderStroke(1.dp, Color(0xFFFFECB3)), Color(0xFFFFF8E1), Color(0xFFC67D00), "复兴号")
            }
        }
        TrainSeries.SLEEPER -> {
            // 动卧：深邃星空夜行风格，在浅色/深色模式下均保持标志性墨蓝夜空底色
            if (isDark) {
                tuple5(Color(0xFF0C101D), BorderStroke(1.dp, Color(0xFF222D4A)), Color(0xFF1E284E), Color(0xFF9FA8DA), "动卧")
            } else {
                tuple5(Color(0xFF131A30), BorderStroke(1.dp, Color(0xFF2D3B62)), Color(0xFF232F55), Color(0xFFBAC7FF), "动卧")
            }
        }
    }
    val cardElevation = if (isDark) 3.dp else 2.dp

    val isSleeper = ticket.trainSeries == TrainSeries.SLEEPER
    val primaryTextColor = if (isDark || isSleeper) Color(0xFFF1F5F2) else Color.Black
    val secondaryTextColor = if (isDark || isSleeper) Color(0xFF9EABA2) else Color.Gray
    val labelTextColor = if (isDark || isSleeper) Color(0xFF88988E) else Color.LightGray
    val trackLineColor = if (isDark || isSleeper) Color(0xFF384A5D) else Color.LightGray
    val dividerColor = if (isDark || isSleeper) Color(0xFF25314D) else Color(0xFFEEEEEE)
    val trainIconTint = if (isDark) {
        if (ticket.isCompleted) seriesBadgeColor else Color(0xFF88988E)
    } else {
        seriesBadgeColor.copy(alpha = 0.7f)
    }

    val seatClassContainer = if (isDark) {
        Color(0xFF1B5E20).copy(alpha = 0.45f)
    } else {
        RailColors.Primary.copy(alpha = 0.1f)
    }
    val seatClassTextColor = if (isDark) Color(0xFF81C784) else RailColors.Primary

    val seatInfoContainer = if (isDark) Color(0xFF253328) else MaterialTheme.colorScheme.surfaceVariant
    val seatInfoTextColor = if (isDark) Color(0xFFD4DDD6) else MaterialTheme.colorScheme.onSurfaceVariant

    val arrivalTextColor = if (ticket.isCompleted) {
        primaryTextColor
    } else {
        if (isDark) Color(0xFF5A6960) else Color.LightGray
    }

    val shareButtonBg = if (isDark) Color(0xFF253328) else Color(0xFFF5F5F5)
    val shareButtonTint = if (isDark) Color(0xFFD4DDD6) else Color.DarkGray

    val pageBgColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .drawWithContent {
                    captureLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(captureLayer)
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (ticket.isCompleted) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { shareTicket(context, captureLayer, ticket) }
                        }
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isSleeper) {
                    // 动卧专属星空粒子背景与微弱极光流动
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // 随机散落的闪耀星星
                        val starCount = 38
                        val seed = ticket.record.id.hashCode()
                        val random = java.util.Random(seed.toLong())
                        for (i in 0 until starCount) {
                            val x = random.nextFloat() * size.width
                            val y = random.nextFloat() * size.height
                            val r = 0.8f + random.nextFloat() * 1.5f
                            val alpha = 0.25f + random.nextFloat() * 0.65f
                            val starColor = if (i % 5 == 0) Color(0xFFFFE082).copy(alpha = alpha) else Color(0xFFE8EAF6).copy(alpha = alpha)
                            drawCircle(
                                color = starColor,
                                radius = r,
                                center = Offset(x, y)
                            )
                        }
                        // 顶部与底部微弱星云蓝光
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF3F51B5).copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.85f, size.height * 0.15f),
                                radius = size.width * 0.6f
                            ),
                            radius = size.width * 0.6f,
                            center = Offset(size.width * 0.85f, size.height * 0.15f)
                        )
                    }
                }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
          // 顶部：日期与状态
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
              Text(
                  text = ticket.ticketDate,
                  style = MaterialTheme.typography.labelMedium,
                  color = secondaryTextColor
              )
               Text(
                   text = if (ticket.isCompleted) stringResource(R.string.history_status_completed) else stringResource(R.string.history_status_cancelled),
                   style = MaterialTheme.typography.labelLarge,
                   color = statusColor,
                   fontWeight = FontWeight.Bold
               )
          }

            Spacer(modifier = Modifier.height(16.dp))

            // 中间：车站、线路与图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧车站
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = ticket.record.startStation.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = primaryTextColor
                    )
                    Text(
                        text = ticket.record.startStation.city,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }

                // 中间虚线 + 图标 + 时长（自适应剩余宽度）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
                            drawLine(
                                color = trackLineColor,
                                start = Offset.Zero,
                                end = Offset(size.width, 0f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        BulletTrainIcon(
                            contentDescription = null,
                            size = 18.dp,
                            tint = trainIconTint,
                        )
                        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
                            drawLine(
                                color = trackLineColor,
                                start = Offset.Zero,
                                end = Offset(size.width, 0f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                   Row(
                       verticalAlignment = Alignment.CenterVertically,
                       horizontalArrangement = Arrangement.spacedBy(4.dp),
                       modifier = Modifier.padding(top = 2.dp)
                   ) {
                       Text(
                           text = ticket.trainNumber,
                           style = MaterialTheme.typography.labelSmall,
                           fontWeight = FontWeight.Bold,
                           color = primaryTextColor
                       )
                   }
                }

                // 右侧车站
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = ticket.record.endStation.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = primaryTextColor
                    )
                    Text(
                        text = ticket.record.endStation.city,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }
            }

            // 车型标签 / 专注类型 / 座位信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = seriesBadgeBg,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = seriesLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = seriesBadgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                ticket.seatClass.let { seatClass ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = seatClassContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = seatClass,
                            style = MaterialTheme.typography.labelSmall,
                            color = seatClassTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
               Surface(
                   shape = RoundedCornerShape(12.dp),
                   color = seatInfoContainer
               ) {
                   Text(
                       text = ticket.seatInfo,
                       style = MaterialTheme.typography.labelSmall,
                       color = seatInfoTextColor,
                       modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                   )
               }
                if (ticket.delayMinutes > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = (if (isDark) Color(0xFFEF5350) else Color(0xFFF44336)).copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_status_delayed, ticket.delayMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFFEF5350) else Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else if (ticket.isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = (if (isDark) Color(0xFF81C784) else Color(0xFF4CAF50)).copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_status_on_time),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFF81C784) else Color(0xFF388E3C),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
           }

           Spacer(modifier = Modifier.weight(1f))

            // 虚线与左右车票半圆打孔
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                DashedDivider(
                    color = dividerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // 底部：详细时间与距离
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.history_departure), style = MaterialTheme.typography.labelSmall, color = labelTextColor)
                    Text(ticket.departureTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.history_distance), style = MaterialTheme.typography.labelSmall, color = labelTextColor)
                    Text("${ticket.record.path.totalDistanceKm.toInt()} " + stringResource(R.string.history_unit_km), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.history_arrival), style = MaterialTheme.typography.labelSmall, color = labelTextColor)
                    Text(
                        ticket.arrivalTime,
                        style = MaterialTheme.typography.bodyMedium, 
                        fontWeight = FontWeight.Bold,
                        color = arrivalTextColor
                    )
                }
            }
        }
        }
        }

        // 左右真实物理打孔缺口（取页面背景色覆盖并带有凹向内部的圆弧描边）
        val strokeColor = when (ticket.trainSeries) {
            TrainSeries.C_SERIES -> if (isDark) Color(0xFF2A453A) else Color(0xFFB2DFDB)
            TrainSeries.D_SERIES -> if (isDark) Color(0xFF263D54) else Color(0xFFBBDEFB)
            TrainSeries.G_SERIES -> if (isDark) Color(0xFF4A3828) else Color(0xFFFFE082)
            TrainSeries.SLEEPER -> if (isDark) Color(0xFF29375A) else Color(0xFF3B4D80)
        }

        // 左打孔缺口
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = (-48).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(pageBgColor)
        )
        if (strokeColor != null) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-10).dp, y = (-48).dp)
                    .size(20.dp)
            ) {
                drawArc(
                    color = strokeColor,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // 右打孔缺口
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = (-48).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(pageBgColor)
        )
        if (strokeColor != null) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = (-48).dp)
                    .size(20.dp)
            ) {
                drawArc(
                    color = strokeColor,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

/**
 * 将车票渲染为 PNG 后通过系统分享面板发出
 */
private suspend fun shareTicket(
    context: android.content.Context,
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    ticket: TrainTicketModel,
) {
    runCatching {
        val bitmap = withContext(Dispatchers.Default) {
            layer.toImageBitmap().asAndroidBitmap()
        }
        val dir = java.io.File(context.cacheDir, "tickets").apply { mkdirs() }
        val file = java.io.File(dir, "ticket_" + ticket.record.id + ".png")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(sendIntent, null))
    }
}

@Composable
private fun DashedDivider(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFEEEEEE),
) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
            strokeWidth = 2f
        )
    }
}

private class TrainTicketShape(
    private val notchSize: Dp,
    private val cornerRadius: Dp = 12.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val notchRadius = with(density) { notchSize.toPx() }
        val corner = with(density) { cornerRadius.toPx() }
        val width = size.width
        val height = size.height
        val notchY = height * 0.7f // Notch position matched to the divider

        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(width - corner, 0f)
            quadraticTo(width, 0f, width, corner)
            lineTo(width, notchY - notchRadius)
            // Right notch
            arcTo(
                rect = Rect(
                    left = width - notchRadius,
                    top = notchY - notchRadius,
                    right = width + notchRadius,
                    bottom = notchY + notchRadius,
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(width, height - corner)
            quadraticTo(width, height, width - corner, height)
            lineTo(corner, height)
            quadraticTo(0f, height, 0f, height - corner)
            lineTo(0f, notchY + notchRadius)
            // Left notch
            arcTo(
                rect = Rect(
                    left = -notchRadius,
                    top = notchY - notchRadius,
                    right = notchRadius,
                    bottom = notchY + notchRadius,
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

private val PreviewSampleTicket = TrainTicketModel(
    record = JourneyRecord(
        id = "preview-id",
        startStation = Station.DEFAULT.copy(name = "南京南"),
        endStation = Station.DEFAULT.copy(name = "北京南"),
        plannedDurationMin = 120,
        actualDurationMin = 118,
        path = PathResult(
            path = listOf(Station.DEFAULT, Station.DEFAULT),
            totalDurationMin = 120,
            totalDistanceKm = 1024.0,
            edges = emptyList()
        ),
        createdAt = System.currentTimeMillis(),
        completedAt = System.currentTimeMillis()
    ),
    ticketDate = "2026年7月3日",
    departureTime = "10:25",
    arrivalTime = "12:25",
    trainNumber = "G124",
    seatInfo = "11车13A号",
    seatClass = "二等座",
    focusMinutes = 118,
    plannedMinutes = 30,
    stationCount = 2,
    isCompleted = true,
    completionStatus = "已完成",
    focusState = "专注达成",
    delayMinutes = 0
)

@Preview(name = "Light Mode Ticket", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun TrainTicketCardLightPreview() {
    RailFocusTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            TrainTicketCard(ticket = PreviewSampleTicket)
        }
    }
}

@Preview(name = "Dark Mode Ticket", showBackground = true, backgroundColor = 0xFF0D130F)
@Composable
private fun TrainTicketCardDarkPreview() {
    RailFocusTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            TrainTicketCard(ticket = PreviewSampleTicket)
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
private fun <A, B, C, D, E> tuple5(a: A, b: B, c: C, d: D, e: E) = Tuple5(a, b, c, d, e)
