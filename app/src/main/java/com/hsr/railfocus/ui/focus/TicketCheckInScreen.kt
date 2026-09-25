package com.hsr.railfocus.ui.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hsr.railfocus.R
import androidx.compose.ui.unit.dp
import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.model.JourneyStatus
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.service.DestinationOption
import com.hsr.railfocus.ui.history.TrainTicketHelper
import com.hsr.railfocus.ui.history.TrainTicketModel
import com.hsr.railfocus.ui.history.components.TrainTicketCard
import com.hsr.railfocus.util.TicketFeedbackHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 发车前：车票悬浮检票页面
 *
 * 悬浮于地图路线之上，车票样式与历史票夹 (TrainTicketCard) 完全一致；
 * 用户轻触车票直接打孔验票并触发清脆音效与触感震动，随后平滑推入专注旅程。
 */
@Composable
fun TicketCheckInScreen(
    destination: DestinationOption,
    focusType: FocusType?,
    seatNumber: String?,
    onDepartStart: (carriageNumber: String) -> Unit,
    onCheckInComplete: (carriageNumber: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val startStation = destination.pathStations.firstOrNull() ?: Station.DEFAULT
    val endStation = destination.station
    val plannedMinutes = destination.travelTimeMinutes

    // 分配车厢与生成车次编号
    val carriageNumber = remember { (1..16).random().toString() }
    val (trainSeries, prefix, maxSpeed) = remember(plannedMinutes, focusType) {
        TrainTicketHelper.determineTrainSeries(plannedMinutes, focusType = focusType?.displayName)
    }
    val trainNumber = remember(prefix, startStation, endStation, plannedMinutes) {
        TrainTicketHelper.synthesizeTrainNumber(prefix, startStation, endStation, plannedMinutes)
    }
    val now = remember { System.currentTimeMillis() }
    val arrivalMillis = remember(now, plannedMinutes) { now + plannedMinutes * 60 * 1000 }
    val ticketDate = remember(now) { TrainTicketHelper.DATE_FORMAT.format(Date(now)) }
    val departureTime = remember(now) { TrainTicketHelper.TIME_FORMAT.format(Date(now)) }
    val arrivalTime = remember(arrivalMillis) { TrainTicketHelper.TIME_FORMAT.format(Date(arrivalMillis)) }
    val defaultSeatClass = stringResource(R.string.ticket_seat_class_second)
    val statusPending = stringResource(R.string.ticket_status_pending_checkin)
    val statusCheckedIn = stringResource(R.string.ticket_status_checked_in)
    val stateWaiting = stringResource(R.string.ticket_focus_state_waiting)
    val stateShowTicket = stringResource(R.string.ticket_focus_state_show_ticket)

    val seatClean = remember(seatNumber) {
        seatNumber?.replace("号", "")?.replace(Regex("^[0-9]+车"), "") ?: "01A"
    }
    val seatInfo = stringResource(R.string.ticket_seat_format, carriageNumber, seatClean)
    val seatClass = focusType?.displayName ?: defaultSeatClass

    // 动画状态定义
    var isPunched by remember { mutableStateOf(false) }
    var isPunchingInProgress by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }

    // 构建与历史车票完全一致的数据模型
    val ticketModel = remember(destination, focusType, seatNumber, carriageNumber, isPunched) {
        val record = JourneyRecord(
            id = "pending-checkin",
            startStation = startStation,
            endStation = endStation,
            plannedDurationMin = plannedMinutes,
            actualDurationMin = 0,
            path = PathResult(
                path = destination.pathStations,
                totalDurationMin = plannedMinutes,
                totalDistanceKm = destination.distance,
                edges = destination.pathEdges,
            ),
            createdAt = now,
            completedAt = null,
            status = JourneyStatus.ACTIVE,
            focusType = focusType?.displayName,
            seatNumber = seatNumber,
            carriageNumber = carriageNumber,
        )
        TrainTicketModel(
            record = record,
            ticketDate = ticketDate,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            trainNumber = trainNumber,
            trainSeries = trainSeries,
            maxSpeed = maxSpeed,
            seatInfo = seatInfo,
            seatClass = seatClass,
            focusMinutes = 0,
            plannedMinutes = plannedMinutes,
            stationCount = destination.pathStations.size,
            isCompleted = false,
            completionStatus = if (isPunched) statusCheckedIn else statusPending,
            focusState = if (isPunched) stateWaiting else stateShowTicket,
            delayMinutes = 0,
        )
    }

    // 车票初次登场悬浮滑入动画 (0f -> 1f)
    val ticketSlideAnim = remember { Animatable(0f) }
    // 车票过闸推入站台动画 (0f -> 1f)
    val ticketDepartAnim = remember { Animatable(0f) }

    // 呼吸悬浮动效
    val infiniteTransition = rememberInfiniteTransition(label = "checkin_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // 预加载音效资源与车票滑入
    LaunchedEffect(Unit) {
        TicketFeedbackHelper.preload(context)
        ticketSlideAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        )
    }

    // 核心点击车票打孔交互：点击即刻打孔，音效与触觉反馈同步触发，无多余外置工具动画
    val handleTicketClick = {
        if (!isPunchingInProgress && !isCompleted) {
            isPunchingInProgress = true
            scope.launch {
                // 1. 打出圆形孔、触发清脆打孔音效、线性触觉双脉冲震动
                isPunched = true
                TicketFeedbackHelper.playPunchSound(context)
                TicketFeedbackHelper.performPunchHaptic(context)

                // 2. 停顿 150ms 呈现打孔效果，随后直接动画收入车票
                delay(150)

                // 3. 动画直接收入车票，进入专注页面。
                // 先把启动服务、初始化旅程等重活抛给宿主在动画期间完成，
                // 动画结束后再切换页面，避免转场瞬间集中阻塞主线程。
                isCompleted = true
                onDepartStart(carriageNumber)
                ticketDepartAnim.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
                onCheckInComplete(carriageNumber)
            }
        }
    }

    // 悬浮层：半透明遮罩浮于地图上方，点击遮罩空白区域可取消返回
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel
            ),
        contentAlignment = Alignment.Center
    ) {
        val enterOffsetY = (1f - ticketSlideAnim.value) * 180f
        val departOffsetY = -ticketDepartAnim.value * 450f
        val departAlpha = 1f - (ticketDepartAnim.value * 0.85f)
        val departScale = 1f - (ticketDepartAnim.value * 0.08f)
        val idleScale = if (!isPunchingInProgress && ticketSlideAnim.value >= 1f) pulseScale else 1f

        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    translationY = enterOffsetY + departOffsetY
                    alpha = departAlpha
                    scaleX = departScale * idleScale
                    scaleY = departScale * idleScale
                }
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = handleTicketClick
                )
        ) {
            // 直接复用历史车票组件，强制浅色模式以呈现图2经典质感
            TrainTicketCard(
                ticket = ticketModel,
                isPunched = isPunched,
                forceDarkTheme = false,
                onClick = handleTicketClick,
                modifier = Modifier.fillMaxWidth()
            )


        }
    }
}
