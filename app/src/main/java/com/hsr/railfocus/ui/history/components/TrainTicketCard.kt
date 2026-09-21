package com.hsr.railfocus.ui.history.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalContext
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
    val captureLayer = rememberGraphicsLayer()
    val notchSize = 16.dp

    // 自动适配深色模式（根据主题背景明度），也可通过 forceDarkTheme 显式指定
    val isDark = forceDarkTheme ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)

    val statusColor = if (isDark) {
        if (ticket.isCompleted) Color(0xFF81C784) else Color(0xFFEF5350)
    } else {
        if (ticket.isCompleted) Color(0xFF4CAF50) else Color(0xFFF44336)
    }

    // 票面背景与边框：深色模式下采用深墨绿车票质感底色与细腻轮廓边框，浅色模式下保持经典白纸车票
    val containerColor = if (isDark) Color(0xFF1B241E) else Color.White
    val cardBorder = if (isDark) BorderStroke(1.dp, Color(0xFF2E3E33)) else null
    val cardElevation = if (isDark) 3.dp else 2.dp

    val primaryTextColor = if (isDark) Color(0xFFF1F5F2) else Color.Black
    val secondaryTextColor = if (isDark) Color(0xFF9EABA2) else Color.Gray
    val labelTextColor = if (isDark) Color(0xFF88988E) else Color.LightGray
    val trackLineColor = if (isDark) Color(0xFF384A3D) else Color.LightGray
    val dividerColor = if (isDark) Color(0xFF2B3B30) else Color(0xFFEEEEEE)
    val trainIconTint = if (isDark) {
        if (ticket.isCompleted) Color(0xFF81C784) else Color(0xFF88988E)
    } else {
        Color.LightGray
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

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight() // 允许内容决定高度，防止在大字体或多内容下被裁剪
                .drawWithContent {
                    captureLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(captureLayer)
                },
            shape = TrainTicketShape(notchSize),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        ) {
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
                    Text(
                        text = "${ticket.plannedMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
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

            // 专注类型 / 座位信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            }

            Spacer(modifier = Modifier.weight(1f))

            // 虚线
            DashedDivider(
                color = dividerColor,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )

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

        if (ticket.isCompleted) {
            IconButton(
                onClick = {
                    scope.launch { shareTicket(context, captureLayer, ticket) }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(shareButtonBg),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.ticket_share),
                    tint = shareButtonTint,
                    modifier = Modifier.size(18.dp),
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
    focusState = "专注达成"
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
