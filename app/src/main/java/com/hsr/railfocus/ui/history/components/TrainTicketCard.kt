package com.hsr.railfocus.ui.history.components

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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureLayer = rememberGraphicsLayer()
    val statusColor = if (ticket.isCompleted) Color(0xFF4CAF50) else Color(0xFFF44336)
    val notchSize = 16.dp

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
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                    color = Color.Gray
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
                        color = Color.Black
                    )
                    Text(
                        text = ticket.record.startStation.city,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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
                                color = Color.LightGray,
                                start = Offset.Zero,
                                end = Offset(size.width, 0f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        BulletTrainIcon(
                            contentDescription = null,
                            size = 18.dp,
                            tint = Color.LightGray,
                        )
                        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
                            drawLine(
                                color = Color.LightGray,
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
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 右侧车站
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = ticket.record.endStation.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Text(
                        text = ticket.record.endStation.city,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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
                        color = RailColors.Primary.copy(alpha = 0.1f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = seatClass,
                            style = MaterialTheme.typography.labelSmall,
                            color = RailColors.Primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = ticket.seatInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 虚线
            DashedDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))

            // 底部：详细时间与距离
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.history_departure), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    Text(ticket.departureTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.history_distance), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    Text("${ticket.record.path.totalDistanceKm.toInt()} " + stringResource(R.string.history_unit_km), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.history_arrival), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    Text(
                        ticket.arrivalTime,
                        style = MaterialTheme.typography.bodyMedium, 
                        fontWeight = FontWeight.Bold,
                        color = if (ticket.isCompleted) Color.Black else Color.LightGray
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
                    .background(Color(0xFFF5F5F5)),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.ticket_share),
                    tint = Color.DarkGray,
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
private fun DashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = Color(0xFFEEEEEE),
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

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun TrainTicketCardPreview() {
    RailFocusTheme {
        val sample = TrainTicketModel(
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
            isCompleted = false,
            completionStatus = "已取消",
            focusState = "专注未达成"
        )

        Box(modifier = Modifier.padding(16.dp)) {
            TrainTicketCard(ticket = sample)
        }
    }
}
