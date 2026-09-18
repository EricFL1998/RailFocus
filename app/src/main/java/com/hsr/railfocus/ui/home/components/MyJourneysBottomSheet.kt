package com.hsr.railfocus.ui.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.JourneyStatus
import com.hsr.railfocus.ui.history.HistoryUiState
import com.hsr.railfocus.ui.history.HistoryViewModel
import com.hsr.railfocus.ui.history.TrainTicketModel
import com.hsr.railfocus.ui.history.components.TrainTicketCard

/**
 * 我的 内容面板 - 卡包风格
 */
@Composable
fun MyJourneysContent(
    uiState: HistoryUiState,
    onSettingsClick: () -> Unit,
    onStatusFilter: (JourneyStatus?) -> Unit,
    onFocusTypeFilter: (String?) -> Unit,
    onSortOrder: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 头部
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.width(48.dp))
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.home_settings),
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // 筛选栏 - 仅在有数据时显示
        if (!uiState.isEmpty) {
            FilterBar(
                selectedStatus = uiState.selectedStatus,
                selectedFocusType = uiState.selectedFocusType,
                availableFocusTypes = uiState.availableFocusTypes,
                isSortedByDateDesc = uiState.isSortedByDateDesc,
                onStatusFilter = onStatusFilter,
                onFocusTypeFilter = onFocusTypeFilter,
                onSortOrder = onSortOrder
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.isEmpty -> {
                    EmptyJourneysState(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    CardStack(tickets = uiState.filteredTickets)
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    selectedStatus: JourneyStatus?,
    selectedFocusType: String?,
    availableFocusTypes: List<String>,
    isSortedByDateDesc: Boolean,
    onStatusFilter: (JourneyStatus?) -> Unit,
    onFocusTypeFilter: (String?) -> Unit,
    onSortOrder: (Boolean) -> Unit
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var focusTypeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Box {
                FilterChip(
                    label = stringResource(R.string.history_filter_status),
                    selected = selectedStatus != null,
                    onClick = { statusMenuExpanded = true },
                    value = when(selectedStatus) {
                        JourneyStatus.COMPLETED -> stringResource(R.string.history_status_completed)
                        JourneyStatus.CANCELLED -> stringResource(R.string.history_status_cancelled)
                        else -> null
                    }
                )
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_filter_all_status)) },
                        onClick = { onStatusFilter(null); statusMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_status_completed)) },
                        onClick = { onStatusFilter(JourneyStatus.COMPLETED); statusMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_status_cancelled)) },
                        onClick = { onStatusFilter(JourneyStatus.CANCELLED); statusMenuExpanded = false }
                    )
                }
            }
        }
        
        item {
            Box {
                FilterChip(
                    label = stringResource(R.string.history_filter_scene),
                    selected = selectedFocusType != null,
                    onClick = { focusTypeMenuExpanded = true },
                    value = selectedFocusType
                )
                DropdownMenu(
                    expanded = focusTypeMenuExpanded,
                    onDismissRequest = { focusTypeMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_filter_all_scenes)) },
                        onClick = { onFocusTypeFilter(null); focusTypeMenuExpanded = false }
                    )
                    availableFocusTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = { onFocusTypeFilter(type); focusTypeMenuExpanded = false }
                        )
                    }
                }
            }
        }

        item {
            Box {
                FilterChip(
                    label = stringResource(R.string.history_filter_sort),
                    selected = !isSortedByDateDesc,
                    onClick = { sortMenuExpanded = true },
                    value = if (isSortedByDateDesc) stringResource(R.string.history_sort_latest) else stringResource(R.string.history_sort_earliest)
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_sort_latest_first)) },
                        onClick = { onSortOrder(true); sortMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_sort_earliest_first)) },
                        onClick = { onSortOrder(false); sortMenuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    value: String? = null,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value ?: label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CardStack(
    tickets: List<TrainTicketModel>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy((-15).dp) // 轻微重叠，保持边缘重叠但核心内容（文字）不被遮挡
    ) {
        itemsIndexed(
            items = tickets,
            key = { _, ticket -> ticket.record.id }
        ) { index, ticket ->
            val rotation = when (index % 4) {
                0 -> -1.5f
                1 -> 1.0f
                2 -> -0.8f
                else -> 1.2f
            }
            
            TrainTicketCard(
                ticket = ticket,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = rotation
                    }
                    .zIndex(index.toFloat())
            )
        }
    }
}

/**
 * 我的 底部弹出面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyJourneysBottomSheet(
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        MyJourneysContent(
            uiState = uiState,
            onSettingsClick = onSettingsClick,
            onStatusFilter = viewModel::setStatusFilter,
            onFocusTypeFilter = viewModel::setFocusTypeFilter,
            onSortOrder = viewModel::setSortOrder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyJourneysState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 450.dp), // 继续向上偏移，适配半高
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        com.hsr.railfocus.ui.components.BulletTrainIcon(
            contentDescription = null,
            modifier = Modifier.padding(bottom = 12.dp),
            size = 56.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        )
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.history_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
