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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val text = "%02d:%02d".format(minutes, seconds)

    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
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
                        modifier = Modifier.weight(1f),
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
                            text = stringResource(R.string.focus_arrived_at_station, currentStation?.name ?: "--"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 中间：速度 0 km/h
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
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

                    // 右侧：下一站
                    StationLabel(
                        title = stringResource(R.string.focus_next_station_label),
                        name = nextStation?.name ?: "--",
                        modifier = Modifier.weight(1f),
                        alignRight = true
                    )
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
                                    softWrap = false
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
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                        
                        val density = LocalDensity.current
                        val dotOffset = with(density) { 36.dp.toPx() }
                        val infiniteTransition = rememberInfiniteTransition(label = "dots_orbit")
                        val baseAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(12000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "base_angle"
                        )
                        repeat(6) { i ->
                            val angle = baseAngle + i * 60f
                            Surface(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer {
                                        translationX = dotOffset * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                                        translationY = dotOffset * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                            ) {}
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (completed) {
                            Text(
                                text = stringResource(R.string.completion_welcome),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = city,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
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
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            ) {
                                Text(
                                    text = stringResource(R.string.completion_stamp_arrived),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
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
                            "文化" -> Icons.Default.Palette
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
                                        text = "${city}${stationFact.category}",
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
