package com.hsr.railfocus.domain.model

enum class JourneyStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

data class JourneyRecord(
    val id: String,
    val startStation: Station,
    val endStation: Station,
    val plannedDurationMin: Int,
    val actualDurationMin: Int,
    val path: PathResult,
    val createdAt: Long,
    val completedAt: Long? = null,
    /** 进行中旅程最近一次检查点记录的剩余秒数（仅 ACTIVE 状态有意义） */
    val remainingSec: Int? = null,
    val status: JourneyStatus = JourneyStatus.COMPLETED,
    val focusType: String? = null,
    val seatNumber: String? = null,
    val carriageNumber: String? = null,
)
