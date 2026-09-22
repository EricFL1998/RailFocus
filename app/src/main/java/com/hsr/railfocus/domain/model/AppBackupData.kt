package com.hsr.railfocus.domain.model

import kotlinx.serialization.Serializable

/**
 * 应用全量数据备份结构
 */
@Serializable
data class AppBackupData(
    val exportVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.7",
    val journeys: List<BackupJourneyRecord> = emptyList(),
    val visitedStationIds: List<String> = emptyList(),
    val focusTypes: List<BackupFocusType> = emptyList(),
    val journals: List<BackupJournal> = emptyList(),
    val statistics: BackupStatistics? = null,
)

@Serializable
data class BackupJourneyRecord(
    val id: String,
    val startStationId: String,
    val endStationId: String,
    val plannedDurationMin: Int,
    val actualDurationMin: Int,
    val pathJson: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val status: String = "COMPLETED",
    val focusType: String? = null,
    val seatNumber: String? = null,
    val carriageNumber: String? = null,
    val delayMinutes: Int = 0,
    val earnedTier: String? = null,
)

@Serializable
data class BackupFocusType(
    val id: String,
    val displayName: String,
    val iconName: String,
    val colorHex: Int,
    val containerColorHex: Int,
    val isRemovable: Boolean,
    val order: Int = 0,
)

@Serializable
data class BackupJournal(
    val id: String,
    val journeyId: String,
    val stationId: String,
    val stationName: String,
    val content: String,
    val imagePathsJson: String = "[]",
    val audioPath: String? = null,
    val audioDurationSec: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    /** 图片二进制内容（base64），保证卸载重装后可完整恢复 */
    val imageFiles: List<BackupMediaFile> = emptyList(),
    /** 语音二进制内容（base64） */
    val audioFile: BackupMediaFile? = null,
)

@Serializable
data class BackupMediaFile(
    val fileName: String,
    val base64: String,
)

@Serializable
data class BackupStatistics(
    val lifetimeFocusMin: Int = 0,
    val focusStreak: Int = 0,
    val membershipTier: String = "SILVER",
)

