package com.hsr.railfocus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "journey_records")
data class JourneyRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startStationId: String,
    val endStationId: String,
    val plannedDurationMin: Int,
    val actualDurationMin: Int,
    val pathJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val status: String = "COMPLETED",
    /** 进行中的旅程在上次落库检查点时的剩余秒数，用于进程被杀后的恢复 */
    val remainingSec: Int? = null,
    val focusType: String? = null,
    val seatNumber: String? = null,
    val carriageNumber: String? = null,
    val delayMinutes: Int = 0,
    val earnedTier: String? = null,
)
