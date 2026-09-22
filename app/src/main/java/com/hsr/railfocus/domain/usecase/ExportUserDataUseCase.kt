package com.hsr.railfocus.domain.usecase

import android.util.Base64
import com.hsr.railfocus.data.local.dataaccess.FocusTypeDataAccess
import com.hsr.railfocus.data.local.dataaccess.JournalDataAccess
import com.hsr.railfocus.data.local.dataaccess.JourneyDataAccess
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.*
import com.hsr.railfocus.util.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

/**
 * 导出全部用户数据 UseCase
 */
class ExportUserDataUseCase @Inject constructor(
    private val journeyDataAccess: JourneyDataAccess,
    private val visitedStationDataAccess: VisitedStationDataAccess,
    private val focusTypeDataAccess: FocusTypeDataAccess,
    private val journalDataAccess: JournalDataAccess,
    private val preferencesRepository: UserPreferencesRepository,
) {
    /**
     * 读取媒体文件并编码为 base64，文件不存在时跳过
     */
    private fun encodeMediaFile(absolutePath: String?): BackupMediaFile? {
        if (absolutePath.isNullOrBlank()) return null
        return try {
            val file = File(absolutePath)
            if (!file.exists() || !file.isFile) return null
            BackupMediaFile(
                fileName = file.name,
                base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun generateBackupData(appVersion: String = "1.7"): AppBackupData = withContext(Dispatchers.IO) {
        val journeys = journeyDataAccess.getAllRecords().map { entity ->
            BackupJourneyRecord(
                id = entity.id,
                startStationId = entity.startStationId,
                endStationId = entity.endStationId,
                plannedDurationMin = entity.plannedDurationMin,
                actualDurationMin = entity.actualDurationMin,
                pathJson = entity.pathJson,
                createdAt = entity.createdAt,
                completedAt = entity.completedAt,
                status = entity.status,
                focusType = entity.focusType,
                seatNumber = entity.seatNumber,
                carriageNumber = entity.carriageNumber,
                delayMinutes = entity.delayMinutes,
                earnedTier = entity.earnedTier,
            )
        }

        val visitedStationIds = visitedStationDataAccess.getAllVisitedStationIds()

        val focusTypes = focusTypeDataAccess.getAllList().map { entity ->
            BackupFocusType(
                id = entity.id,
                displayName = entity.displayName,
                iconName = entity.iconName,
                colorHex = entity.colorHex,
                containerColorHex = entity.containerColorHex,
                isRemovable = entity.isRemovable,
                order = entity.order,
            )
        }

        val journals = journalDataAccess.getAllJournals().map { entity ->
            val imagePaths = try {
                appJson.decodeFromString<List<String>>(entity.imagePathsJson)
            } catch (_: Exception) {
                emptyList()
            }
            BackupJournal(
                id = entity.id,
                journeyId = entity.journeyId,
                stationId = entity.stationId,
                stationName = entity.stationName,
                content = entity.content,
                imagePathsJson = entity.imagePathsJson,
                audioPath = entity.audioPath,
                audioDurationSec = entity.audioDurationSec,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                imageFiles = imagePaths.mapNotNull { encodeMediaFile(it) },
                audioFile = encodeMediaFile(entity.audioPath),
            )
        }

        val flyerState = preferencesRepository.frequentFlyerState.first()
        val dailyState = preferencesRepository.dailyGoalState.first()
        val stats = BackupStatistics(
            lifetimeFocusMin = flyerState.totalFocusMinutes,
            focusStreak = dailyState.streakDays,
            membershipTier = flyerState.tier.name,
        )

        AppBackupData(
            exportVersion = 1,
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            journeys = journeys,
            visitedStationIds = visitedStationIds,
            focusTypes = focusTypes,
            journals = journals,
            statistics = stats,
        )
    }

    suspend fun exportToStream(outputStream: OutputStream, appVersion: String = "1.7"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val data = generateBackupData(appVersion)
            val jsonString = appJson.encodeToString(data)
            outputStream.use { stream ->
                stream.write(jsonString.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            Result.success(data.journeys.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

