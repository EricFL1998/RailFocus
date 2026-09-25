package com.hsr.railfocus.domain.usecase

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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * 导出全部用户数据 UseCase - 流式 ZIP 容器实现
 */
class ExportUserDataUseCase @Inject constructor(
    private val journeyDataAccess: JourneyDataAccess,
    private val visitedStationDataAccess: VisitedStationDataAccess,
    private val focusTypeDataAccess: FocusTypeDataAccess,
    private val journalDataAccess: JournalDataAccess,
    private val preferencesRepository: UserPreferencesRepository,
) {
    suspend fun generateBackupData(appVersion: String = "1.8"): AppBackupData = withContext(Dispatchers.IO) {
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
                imageFiles = emptyList(),
                audioFile = null,
            )
        }

        val flyerState = preferencesRepository.frequentFlyerState.first()
        val stats = BackupStatistics(
            lifetimeFocusMin = flyerState.totalFocusMinutes,
            focusStreak = 0,
            membershipTier = flyerState.tier.name,
        )

        AppBackupData(
            exportVersion = 2,
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            journeys = journeys,
            visitedStationIds = visitedStationIds,
            focusTypes = focusTypes,
            journals = journals,
            statistics = stats,
        )
    }

    suspend fun exportToStream(outputStream: OutputStream, appVersion: String = "1.8"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val data = generateBackupData(appVersion)
            val jsonString = appJson.encodeToString(data)

            ZipOutputStream(outputStream.buffered()).use { zipOut ->
                // 1. 写入元数据 data.json
                zipOut.putNextEntry(ZipEntry("data.json"))
                zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. 流式写入手账媒体文件（图片与音频）
                // 复用 generateBackupData 已查询的手账列表，避免重复全表查询
                val addedEntries = mutableSetOf<String>()
                for (journal in data.journals) {
                    val jId = journal.journeyId
                    val imagePaths: List<String> = try {
                        appJson.decodeFromString(journal.imagePathsJson)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    for (p in imagePaths) {
                        writeMediaEntry(zipOut, jId, p, addedEntries)
                    }
                    journal.audioPath?.let { audioPath ->
                        writeMediaEntry(zipOut, jId, audioPath, addedEntries)
                    }
                }
                zipOut.flush()
            }
            Result.success(data.journeys.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeMediaEntry(
        zipOut: ZipOutputStream,
        journeyId: String,
        filePath: String,
        addedEntries: MutableSet<String>,
    ) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return
        val entryName = "media/journals/$journeyId/${file.name}"
        if (!addedEntries.add(entryName)) return

        zipOut.putNextEntry(ZipEntry(entryName))
        file.inputStream().buffered().use { input ->
            input.copyTo(zipOut, bufferSize = 8192)
        }
        zipOut.closeEntry()
    }
}
