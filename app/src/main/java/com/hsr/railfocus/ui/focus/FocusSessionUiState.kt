package com.hsr.railfocus.ui.focus

import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station

/**
 * 专注页面 UI 状态
 */
data class FocusSessionUiState(
    /** 当前旅程在数据库中的记录 ID（ACTIVE 记录） */
    val journeyId: String? = null,
    val startStation: Station = Station.DEFAULT,
    val endStation: Station = Station.DEFAULT,
    val path: PathResult? = null,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val overallProgress: Float = 0f,
    val currentStation: Station? = null,
    val nextStation: Station? = null,
    val currentSpeed: Float = 0f,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val isStopped: Boolean = false,
    val focusType: FocusType? = null,
    val seatNumber: String? = null,
    val carriageNumber: String? = null,
    val stationFact: com.hsr.railfocus.domain.model.StationFact? = null,
    val error: String? = null,
    /** 是否为从数据库恢复的旅程 */
    val isRestored: Boolean = false,
    /** 恢复旅程时重建的 DestinationOption JSON，用于重启前台服务 */
    val restoredDestinationJson: String = "",
) {
    val isRunning: Boolean
        get() = !isPaused && !isCompleted && !isStopped
}
