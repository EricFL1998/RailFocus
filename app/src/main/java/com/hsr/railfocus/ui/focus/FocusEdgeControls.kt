package com.hsr.railfocus.ui.focus

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hsr.railfocus.R
import com.hsr.railfocus.ui.theme.LocalReduceMotion
import com.hsr.railfocus.ui.theme.Motion
import com.hsr.railfocus.ui.theme.RailMaterial
import kotlinx.coroutines.delay

/**
 * 专注页顶部居中玻璃控件组，30s 后降到 35% 透明。
 * 双击屏幕进入/退出纯净模式。
 */
@Composable
fun FocusEdgeControls(
    isPaused: Boolean,
    isPureMode: Boolean,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onToggleAmbient: () -> Unit,
    ambientEnabled: Boolean,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current

    // 30 秒无操作自动淡出
    var interactionTick by remember { mutableIntStateOf(0) }
    val chromeVisible = remember { mutableStateOf(true) }
    LaunchedEffect(interactionTick) {
        chromeVisible.value = true
        delay(30_000L)
        if (!isPureMode) chromeVisible.value = false
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (chromeVisible.value) 1f else 0.35f,
        animationSpec = if (reduceMotion) Motion.enter() else Motion.settle,
        label = "chrome_alpha",
    )

    var showMoreMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        interactionTick++
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
    ) {
        if (!isPureMode) {
            RailMaterial.GlassSurface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .graphicsLayer { alpha = chromeAlpha },
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 暂停/继续
                    GlassCircleButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPauseToggle()
                        },
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = stringResource(if (isPaused) R.string.focus_resume else R.string.focus_pause),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }

                    // 更多（含结束旅程、白噪音）
                    Box {
                        GlassCircleButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMoreMenu = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.focus_more),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.focus_stop)) },
                                onClick = {
                                    showMoreMenu = false
                                    onStop()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (ambientEnabled) R.string.focus_ambient_muted else R.string.focus_ambient_playing)) },
                                onClick = {
                                    showMoreMenu = false
                                    onToggleAmbient()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    RailMaterial.GlassSurface(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = androidx.compose.foundation.shape.CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

