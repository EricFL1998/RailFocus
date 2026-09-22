package com.hsr.railfocus.ui.history

import com.hsr.railfocus.domain.model.JourneyRecord

/**
 * 历史/我的 页面 UI 状态
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val tickets: List<TrainTicketModel> = emptyList(),
    val filteredTickets: List<TrainTicketModel> = emptyList(),
    val selectedStatus: com.hsr.railfocus.domain.model.JourneyStatus? = null,
    val selectedFocusType: String? = null,
    val availableFocusTypes: List<String> = emptyList(),
    val isSortedByDateDesc: Boolean = true,
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && tickets.isEmpty() && error == null
}

enum class TrainSeries {
    C_SERIES,  // 城际动车 15~25min
    D_SERIES,  // 和谐号 30~45min
    G_SERIES,  // 复兴号 60min+
    SLEEPER,   // 动卧/夜行特快
}

/**
 * 火车票卡片模型
 *
 * 将一次完成的专注旅程映射成类似真实火车票的展示字段。
 */
data class TrainTicketModel(
    val record: JourneyRecord,
    val ticketDate: String,        // e.g. "2026年07月07日"
    val departureTime: String,     // e.g. "12:21"
    val arrivalTime: String,       // e.g. "14:21"
    val trainNumber: String,       // e.g. "G124"
    val trainSeries: TrainSeries = TrainSeries.G_SERIES,
    val maxSpeed: Int = 350,
    val seatInfo: String,          // e.g. "11车13A号"
    val seatClass: String,         // e.g. "二等座"
    val focusMinutes: Int,         // 实际专注分钟数
    val plannedMinutes: Int,       // 计划专注分钟数
    val stationCount: Int,         // 途经站数
    val isCompleted: Boolean,      // 是否已完成
    val completionStatus: String,  // e.g. "已完成"
    val focusState: String,        // e.g. "专注达成"
    val delayMinutes: Int = 0,     // 晚点分钟数
    val memberTierName: String? = null, // 常客会员身份标识 e.g. "GOLD"
)
