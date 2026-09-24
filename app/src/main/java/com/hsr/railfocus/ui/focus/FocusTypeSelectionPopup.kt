package com.hsr.railfocus.ui.focus

import com.hsr.railfocus.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusTypeSelectionPopup(
    focusTypes: List<FocusType>,
    onFocusTypeSelected: (FocusType, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSeat by remember { mutableStateOf<String?>(null) }
    var selectedSeatOffset by remember { mutableStateOf(Offset.Zero) }
    
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(1000f) }

    LaunchedEffect(Unit) {
        offsetY.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
    }

    BackHandler(enabled = true) {
        if (selectedSeat != null) {
            selectedSeat = null
        } else {
            scope.launch {
                offsetY.animateTo(2000f, tween(350, easing = FastOutSlowInEasing))
                onDismiss()
            }
        }
    }

    val draggableState = rememberDraggableState { delta ->
        val newValue = offsetY.value + delta
        if (newValue >= 0f) {
            scope.launch { offsetY.snapTo(newValue) }
        }
    }

    var containerOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .onGloballyPositioned { containerOffset = it.positionInRoot() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        // 自适应高度：由内容决定，但最大不超过屏幕的 90%
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = maxHeight * 0.9f)
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if ((offsetY.value > 250f || velocity > 800f)) {
                            scope.launch {
                                offsetY.animateTo(2000f, tween(350, easing = FastOutSlowInEasing))
                                onDismiss()
                            }
                        } else {
                            scope.launch {
                                offsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .navigationBarsPadding() // 内部避让导航栏，外部背景延伸
                    .padding(bottom = 24.dp) // 基础呼吸空间
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(24.dp)) // 稍微压缩顶部间距

                SeatSelectionExpressive(
                    selectedSeat = selectedSeat,
                ) { seat, offset -> 
                    selectedSeat = seat
                    selectedSeatOffset = offset
                }
            }
        }

        // --- FOCUS TYPE POPUP (Outside the Surface to prevent truncation) ---
        var isProceedingToSession by remember { mutableStateOf(value = false) }

        AnimatedVisibility(
            visible = selectedSeat != null && !isProceedingToSession,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier.fillMaxSize()
        ) {
            selectedSeat?.let { seat ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { selectedSeat = null }
                        )
                ) {
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .offset {
                                val relX = selectedSeatOffset.x - containerOffset.x
                                val relY = selectedSeatOffset.y - containerOffset.y
                                
                                // Anchor popup bottom higher above the seat top
                                val popupHeightPx = with(density) { 280.dp.roundToPx() }
                                val targetY = (relY - popupHeightPx - with(density) { 10.dp.roundToPx() }).toInt()
                                val clampedY = targetY.coerceAtLeast(with(density) { 48.dp.roundToPx() })
                                
                                val screenWidthPx = this@BoxWithConstraints.constraints.maxWidth
                                val seatCenterX = relX + with(density) { 22.dp.roundToPx() }
                                val isRightSide = seatCenterX > screenWidthPx / 2f
                                
                                val paddingPx = with(density) { 24.dp.roundToPx() * 2 }
                                val popupWidthPx = screenWidthPx - paddingPx
                                
                                val xOffset = if (!isRightSide) {
                                    (seatCenterX - screenWidthPx / 2f + popupWidthPx / 4f).toInt()
                                } else {
                                    (seatCenterX - screenWidthPx / 2f - popupWidthPx / 4f).toInt()
                                }
                                
                                val maxShift = with(density) { 12.dp.roundToPx() }
                                val clampedX = xOffset.coerceIn(-maxShift, maxShift)
                                
                                IntOffset(clampedX, clampedY)
                            }
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FocusTypeSubPopupExpressive(
                            seat = seat,
                            focusTypes = focusTypes,
                            onTypeSelected = { type -> 
                                isProceedingToSession = true
                                scope.launch {
                                    offsetY.animateTo(2000f, tween(300, easing = FastOutSlowInEasing))
                                    onFocusTypeSelected(type, seat)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatSelectionExpressive(
    selectedSeat: String?,
    onSeatSelected: (String, Offset) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.seat_pick_title),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.seat_pick_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(6) { rowIdx ->
                val rowNum = "%02d".format(rowIdx + 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveSeatDot("A", selectedSeat == "$rowNum A") { onSeatSelected("$rowNum A", it) }
                        ExpressiveSeatDot("B", selectedSeat == "$rowNum B") { onSeatSelected("$rowNum B", it) }
                        ExpressiveSeatDot("C", selectedSeat == "$rowNum C") { onSeatSelected("$rowNum C", it) }
                    }

                    Text(
                        text = rowNum,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveSeatDot("D", selectedSeat == "$rowNum D") { onSeatSelected("$rowNum D", it) }
                        ExpressiveSeatDot("F", selectedSeat == "$rowNum F") { onSeatSelected("$rowNum F", it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveSeatDot(
    label: String,
    isSelected: Boolean,
    onClick: (Offset) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        animationSpec = tween(300),
        label = "seat_bg_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "seat_content_color"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .onGloballyPositioned { layoutCoordinates ->
                currentOffset = layoutCoordinates.positionInRoot()
            }
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(currentOffset) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusTypeSubPopupExpressive(
    seat: String,
    focusTypes: List<FocusType>,
    onTypeSelected: (FocusType) -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.seat_label, seat),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                text = stringResource(R.string.scene_pick_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            val chunkedTypes = focusTypes.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunkedTypes.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { type ->
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveTypeChip(type = type, onClick = { onTypeSelected(type) })
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveTypeChip(
    type: FocusType,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(16.dp),
        // 专注场景保留各自的原始配色，深色模式下也不叠加暗色遮罩
        color = type.containerColor,
        contentColor = type.color,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = type.color
            )
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = type.color,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
