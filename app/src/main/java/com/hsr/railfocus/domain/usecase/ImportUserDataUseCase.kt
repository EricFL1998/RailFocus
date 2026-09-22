package com.hsr.railfocus.domain.usecase

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hsr.railfocus.data.local.dataaccess.FocusTypeDataAccess
import com.hsr.railfocus.data.local.dataaccess.JournalDataAccess
import com.hsr.railfocus.data.local.dataaccess.JourneyDataAccess
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import com.hsr.railfocus.data.local.entity.FocusTypeEntity
import com.hsr.railfocus.data.local.entity.JourneyJournalEntity
import com.hsr.railfocus.data.local.entity.JourneyRecordEntity
import com.hsr.railfocus.data.local.entity.VisitedStationRecordEntity
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.AppBackupData
import com.hsr.railfocus.domain.model.BackupMediaFile
import com.hsr.railfocus.util.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * 导入全量用户数据 UseCase
 *
 * 合并策略：按主键 REPLACE，重复导入同一份备份是幂等的。
 * 导入的旅程一律落为已完成状态，避免把已结束的旧旅程恢复成进行中。
 */
class ImportUserDataUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val journeyDataAccess: JourneyDataAccess,
    private val visitedStationDataAccess: VisitedStationDataAccess,
    private val focusTypeDataAccess: FocusTypeDataAccess,
    private val journalDataAccess: JournalDataAccess,
    private val preferencesRepository: UserPreferencesRepository,
) {
    data class ImportSummary(
        val journeys: Int,
        val visitedStations: Int,
        val focusTypes: Int,
        val journals: Int,
    )

    suspend fun importFromStream(inputStream: InputStream): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            val jsonString = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val backup = appJson.decodeFromString<AppBackupData>(jsonString)

            // 1. 旅程：恢复为已完成状态，同 id 覆盖
            for (record in backup.journeys) {
                journeyDataAccess.insert(
                    JourneyRecordEntity(
                        id = record.id,
                        startStationId = record.startStationId,
                        endStationId = record.endStationId,
                        plannedDurationMin = record.plannedDurationMin,
                        actualDurationMin = record.actualDurationMin,
                        pathJson = record.pathJson,
                        createdAt = record.createdAt,
                        completedAt = record.completedAt,
                        status = "COMPLETED",
                        remainingSec = null,
                        focusType = record.focusType,
                        seatNumber = record.seatNumber,
                        carriageNumber = record.carriageNumber,
                        delayMinutes = record.delayMinutes,
                        earnedTier = record.earnedTier,
                    )
                )
            }

            // 2. 打卡车站：备份只保留车站 id，恢复时用占位旅程 id
            for (stationId in backup.visitedStationIds) {
                visitedStationDataAccess.insert(
                    VisitedStationRecordEntity(
                        stationId = stationId,
                        journeyId = "",
                    )
                )
            }

            // 3. 专注类型：同 id 覆盖
            for (focusType in backup.focusTypes) {
                focusTypeDataAccess.insert(
                    FocusTypeEntity(
                        id = focusType.id,
                        displayName = focusType.displayName,
                        iconName = focusType.iconName,
                        colorHex = focusType.colorHex,
                        containerColorHex = focusType.containerColorHex,
                        isRemovable = focusType.isRemovable,
                        order = focusType.order,
                    )
                )
            }

            // 4. 手账：依赖旅程，必须在旅程之后写入；同 id 覆盖。
            //    图片与语音二进制写回 files/journals/{journeyId}/，保证卸载重装后可恢复
            for (journal in backup.journals) {
                val journalDir = File(context.filesDir, "journals/${journal.journeyId}").apply { mkdirs() }

                val restoredImagePaths = journal.imageFiles.mapNotNull { media ->
                    writeMediaFile(journalDir, media)
                }
                val restoredImagePathsJson = if (restoredImagePaths.isNotEmpty()) {
                    appJson.encodeToString(restoredImagePaths)
                } else {
                    journal.imagePathsJson
                }

                val restoredAudioPath = journal.audioFile?.let { media ->
                    writeMediaFile(journalDir, media)
                }

                journalDataAccess.insert(
                    JourneyJournalEntity(
                        id = journal.id,
                        journeyId = journal.journeyId,
                        stationId = journal.stationId,
                        stationName = journal.stationName,
                        content = journal.content,
                        imagePathsJson = restoredImagePathsJson,
                        audioPath = restoredAudioPath ?: journal.audioPath,
                        audioDurationSec = journal.audioDurationSec,
                        createdAt = journal.createdAt,
                        updatedAt = journal.updatedAt,
                    )
                )
            }

            // 5. 统计信息
            backup.statistics?.let { stats ->
                preferencesRepository.restoreStatistics(
                    lifetimeMin = stats.lifetimeFocusMin,
                    streakDays = stats.focusStreak,
                    tierName = stats.membershipTier,
                )
            }

            // 6. 主页位置：不单独备份位置，取最近一次旅程的终点恢复；
            //    主页通过 lastLocation 流程即时跟随，导入后无需重新定位
            try {
                val lastJourney = backup.journeys.maxByOrNull { it.completedAt ?: it.createdAt }
                val path = lastJourney?.let {
                    appJson.decodeFromString<com.hsr.railfocus.domain.model.PathResult>(it.pathJson)
                }
                path?.path?.lastOrNull()?.let { endStation ->
                    preferencesRepository.saveLastLocation(
                        com.hsr.railfocus.data.preferences.SavedLocation(
                            latitude = endStation.lat,
                            longitude = endStation.lng,
                            stationId = endStation.id,
                            stationName = endStation.name,
                            city = endStation.city,
                        )
                    )
                }
            } catch (_: Exception) {
                // 位置恢复失败不影响其他数据
            }

            Result.success(
                ImportSummary(
                    journeys = backup.journeys.size,
                    visitedStations = backup.visitedStationIds.size,
                    focusTypes = backup.focusTypes.size,
                    journals = backup.journals.size,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 将备份中的 base64 媒体文件写回私有目录，返回新的绝对路径；失败返回 null
     */
    private fun writeMediaFile(dir: File, media: BackupMediaFile): String? {
        return try {
            val bytes = Base64.decode(media.base64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            // 只取文件名部分，防止备份中的路径穿越
            val safeName = File(media.fileName).name
            val target = File(dir, safeName)
            target.writeBytes(bytes)
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
