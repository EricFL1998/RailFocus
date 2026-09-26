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
    /** 是否正在途径站停靠（停靠时速度为 0） */
    val isDwelling: Boolean = false,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val isStopped: Boolean = false,
    val focusType: FocusType? = null,
    val seatNumber: String? = null,
    val carriageNumber: String? = null,
    val stationFact: com.hsr.railfocus.domain.model.StationFact? = null,
    val error: String? = null,
    /** 累计晚点分钟数 */
    val delayMinutes: Int = 0,
    /** 是否为从数据库恢复的旅程 */
    val isRestored: Boolean = false,
    /** 恢复旅程时重建的 DestinationOption JSON，用于重启前台服务 */
    val restoredDestinationJson: String = "",
    /** 当前旅程关联的手账（若已写手账） */
    val journal: com.hsr.railfocus.domain.model.JourneyJournal? = null,
    /** 【那年今日 · 车站旧忆】时光唤醒 */
    val memoryRecall: com.hsr.railfocus.domain.model.MemoryRecall? = null,
) {
    val isRunning: Boolean
        get() = !isPaused && !isCompleted && !isStopped
}
