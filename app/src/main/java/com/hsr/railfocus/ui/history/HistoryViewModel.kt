package com.hsr.railfocus.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.data.preferences.DailyGoalState
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.usecase.GetJourneyHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 历史/我的 页面 ViewModel
 *
 * 负责：
 * 1. 观察已完成的旅程记录
 * 2. 将旅程数据映射为火车票展示模型
 * 3. 暴露 UI 状态
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getJourneyHistoryUseCase: GetJourneyHistoryUseCase,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** 每日专注目标与连续打卡状态 */
    val dailyGoalState: StateFlow<DailyGoalState> = preferencesRepository.dailyGoalState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyGoalState(45, 0, 0))

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setDailyGoal(minutes) }
    }

    init {
        observeHistory()
    }

    private fun observeHistory() {
        getJourneyHistoryUseCase()
            .onEach { records ->
                val tickets = records
                    .asSequence()
                    // 排除进行中的旅程；完成的和取消的（未完成车票）都展示
                    .filter { it.status != com.hsr.railfocus.domain.model.JourneyStatus.ACTIVE }
                    .map { it.toTrainTicketModel() }
                    .toList()

                val availableTypes = tickets.asSequence().mapNotNull { it.record.focusType }.distinct().sorted().toList()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tickets = tickets,
                        filteredTickets = tickets,
                        availableFocusTypes = availableTypes,
                        error = null,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun setStatusFilter(status: com.hsr.railfocus.domain.model.JourneyStatus?) {
        _uiState.update { it.copy(selectedStatus = status) }
        applyFilters()
    }

    fun setFocusTypeFilter(type: String?) {
        _uiState.update { it.copy(selectedFocusType = type) }
        applyFilters()
    }

    fun setSortOrder(descending: Boolean) {
        _uiState.update { it.copy(isSortedByDateDesc = descending) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = state.tickets

        state.selectedStatus?.let { status ->
            filtered = filtered.filter { it.record.status == status }
        }

        state.selectedFocusType?.let { type ->
            filtered = filtered.filter { it.record.focusType == type }
        }

        filtered = if (state.isSortedByDateDesc) {
            filtered.sortedByDescending { it.record.completedAt ?: it.record.createdAt }
        } else {
            filtered.sortedBy { it.record.completedAt ?: it.record.createdAt }
        }

        _uiState.update { it.copy(filteredTickets = filtered) }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
        private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.CHINA)

        private val TRAIN_PREFIXES = listOf("G", "D", "C", "Z", "T", "K")
        private val SEAT_ROWS = (1..16)
        private val SEAT_LETTERS = listOf("A", "B", "C", "D", "F")
        private val SEAT_CLASSES = listOf("二等座", "一等座", "商务座")

        fun JourneyRecord.toTrainTicketModel(): TrainTicketModel {
            val ticketDate = DATE_FORMAT.format(Date(createdAt))
            val departureTime = TIME_FORMAT.format(Date(createdAt))
            val arrivalTime = completedAt?.let { TIME_FORMAT.format(Date(it)) } ?: "---"

            val trainNumber = synthesizeTrainNumber()
            val seatInfo = if (carriageNumber != null && seatNumber != null) {
                "${carriageNumber}车${seatNumber}号"
            } else {
                synthesizeSeatInfo()
            }
            val seatClass = focusType?.let { typeName ->
                // Since FocusType is no longer an enum, we just use the typeName (which is the displayName)
                // or we could look up the FocusType from a repository if we needed more info.
                typeName
            } ?: synthesizeSeatClass()

            val stationCount = path.path.size
            val isCompleted = status == com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED

            return TrainTicketModel(
                record = this,
                ticketDate = ticketDate,
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                trainNumber = trainNumber,
                seatInfo = seatInfo,
                seatClass = seatClass,
                focusMinutes = actualDurationMin,
                plannedMinutes = plannedDurationMin,
                stationCount = stationCount,
                isCompleted = isCompleted,
                completionStatus = if (isCompleted) "已完成" else "已取消",
                focusState = if (isCompleted) {
                    if (actualDurationMin >= plannedDurationMin) "专注达成" else "专注未完成"
                } else {
                    "专注未达成"
                },
            )
        }

        private fun JourneyRecord.synthesizeTrainNumber(): String {
            // Deterministic "train number" derived from start/end station ids.
            val start = startStation.id.firstOrNull()?.uppercaseChar() ?: 'G'
            val end = endStation.id.firstOrNull()?.uppercaseChar() ?: '1'
            val prefix = if (TRAIN_PREFIXES.any { it[0] == start }) start.toString() else "G"
            val digits = ((start.code + end.code + plannedDurationMin) % 900 + 100).toString()
            return "$prefix$digits"
        }

        private fun JourneyRecord.synthesizeSeatInfo(): String {
            val row = SEAT_ROWS.elementAt((startStation.id.hashCode()).mod(SEAT_ROWS.count()).coerceIn(0, SEAT_ROWS.count() - 1))
            val letter = SEAT_LETTERS.elementAt((endStation.id.hashCode()).mod(SEAT_LETTERS.size))
            return row.toString() + "车" + row + letter + "号"
        }

        private fun JourneyRecord.synthesizeSeatClass(): String {
            return SEAT_CLASSES.elementAt((startStation.id.hashCode()).mod(SEAT_CLASSES.size))
        }

        private fun Int.mod(other: Int): Int = (this % other).let { if (it < 0) it + other else it }
    }
}
