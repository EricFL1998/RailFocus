package com.hsr.railfocus.ui.timeselection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.activity.compose.PredictiveBackHandler
import com.hsr.railfocus.domain.service.DestinationOption
import com.hsr.railfocus.ui.timeselection.components.JourneySelectionContent


/**
 * 时间选择页面
 *
 * 现在内部复用 [JourneySelectionContent] 实现。
 */
@Composable
fun TimeSelectionScreen(
    onBack: () -> Unit,
    onStartFocus: (DestinationOption) -> Unit,
    viewModel: TimeSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    // 使用 PredictiveBackHandler 提供更现代的手势返回体验
    PredictiveBackHandler { progressFlow ->
        try {
            progressFlow.collect { _ -> }
            onBack()
        } catch (_: Exception) {}
    }

    JourneySelectionContent(
        uiState = uiState,
        searchQuery = searchQuery,
        searchResults = searchResults,
        onBack = onBack,
        onStartFocus = onStartFocus,
        onShowStationSearch = { viewModel.setShowStationSearch(show = true) },
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onStationSelected = { station ->
            viewModel.updateStartStation(station)
            viewModel.setShowStationSearch(show = false)
        },
        onDurationSelected = viewModel::onDurationSelected,
        onDestinationSelected = viewModel::onDestinationSelected,
        showBackArrow = false, // 移除返回按钮，使用手势返回
    )
}
