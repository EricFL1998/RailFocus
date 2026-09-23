package com.hsr.railfocus.domain.usecase

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hsr.railfocus.data.local.UserDatabase
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
import kotlinx.serialization.encodeToString
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * 导入全量用户数据 UseCase - 支持流式 ZIP 容器与旧版 JSON 格式自适应
 */
class ImportUserDataUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userDatabase: UserDatabase,
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
            // 导入语义为还原：先清除旧的手账媒体目录，避免旧手账的孤儿文件残留
            File(context.filesDir, "journals").deleteRecursively()

            val buffered = BufferedInputStream(inputStream)
            buffered.mark(4)
            val header = ByteArray(4)
            val readCount = buffered.read(header)
            buffered.reset()

            val isZip = readCount == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()

            if (isZip) {
                importFromZip(buffered)
            } else {
                importFromLegacyJson(buffered)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importFromZip(inputStream: InputStream): Result<ImportSummary> {
        var backupData: AppBackupData? = null
        val restoredMediaMap = mutableMapOf<String, String>()
        // use {} 保证解析中途异常时流一定关闭
        ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "data.json") {
                    val out = ByteArrayOutputStream()
                    zipIn.copyTo(out, bufferSize = 8192)
                    val jsonString = out.toString("UTF-8")
                    backupData = appJson.decodeFromString<AppBackupData>(jsonString)
                } else if (name.startsWith("media/journals/")) {
                    val parts = name.split('/')
                    if (parts.size >= 4) {
                        val journeyId = parts[2]
                        val fileName = File(parts.last()).name
                        val dir = File(context.filesDir, "journals/$journeyId").apply { mkdirs() }
                        val targetFile = File(dir, fileName)
                        FileOutputStream(targetFile).use { fileOut ->
                            zipIn.copyTo(fileOut, bufferSize = 8192)
                        }
                        restoredMediaMap["$journeyId/$fileName"] = targetFile.absolutePath
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        val backup = backupData ?: return Result.failure(Exception("备份文件不包含有效的 data.json 数据"))
        return saveBackupData(backup, restoredMediaMap)
    }

    private suspend fun importFromLegacyJson(inputStream: InputStream): Result<ImportSummary> {
        val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val backup = appJson.decodeFromString<AppBackupData>(jsonString)
        val restoredMediaMap = mutableMapOf<String, String>()

        for (journal in backup.journals) {
            val dir = File(context.filesDir, "journals/${journal.journeyId}").apply { mkdirs() }
            for (media in journal.imageFiles) {
                writeMediaFile(dir, media)?.let { path ->
                    restoredMediaMap["${journal.journeyId}/${media.fileName}"] = path
                }
            }
            journal.audioFile?.let { media ->
                writeMediaFile(dir, media)?.let { path ->
                    restoredMediaMap["${journal.journeyId}/${media.fileName}"] = path
                }
            }
        }
        return saveBackupData(backup, restoredMediaMap)
    }

    private suspend fun saveBackupData(
        backup: AppBackupData,
        restoredMediaMap: Map<String, String>,
    ): Result<ImportSummary> {
        userDatabase.withTransaction {
            // 0. 清空旧数据：导入为还原语义，避免与已有数据合并产生重复记录
            journeyDataAccess.deleteAll()
            visitedStationDataAccess.deleteAll()
            focusTypeDataAccess.deleteAll()
            journalDataAccess.deleteAll()

            // 1. 旅程
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
                        status = record.status,
                        remainingSec = null,
                        focusType = record.focusType,
                        seatNumber = record.seatNumber,
                        carriageNumber = record.carriageNumber,
                        delayMinutes = record.delayMinutes,
                        earnedTier = record.earnedTier,
                    )
                )
            }

            // 2. 打卡车站
            for (stationId in backup.visitedStationIds) {
                visitedStationDataAccess.insert(
                    VisitedStationRecordEntity(
                        stationId = stationId,
                        journeyId = "",
                    )
                )
            }

            // 3. 专注类型
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

            // 4. 手账记录
            for (journal in backup.journals) {
                val jId = journal.journeyId
                val targetDir = File(context.filesDir, "journals/$jId").apply { mkdirs() }

                val originalPaths = try {
                    appJson.decodeFromString<List<String>>(journal.imagePathsJson)
                } catch (_: Exception) {
                    emptyList()
                }

                val finalImagePaths = originalPaths.map { orig ->
                    val fName = File(orig).name
                    restoredMediaMap["$jId/$fName"] ?: File(targetDir, fName).absolutePath
                }

                val finalAudioPath = journal.audioPath?.let { orig ->
                    val fName = File(orig).name
                    restoredMediaMap["$jId/$fName"] ?: File(targetDir, fName).absolutePath
                }

                journalDataAccess.insert(
                    JourneyJournalEntity(
                        id = journal.id,
                        journeyId = journal.journeyId,
                        stationId = journal.stationId,
                        stationName = journal.stationName,
                        content = journal.content,
                        imagePathsJson = appJson.encodeToString(finalImagePaths),
                        audioPath = finalAudioPath,
                        audioDurationSec = journal.audioDurationSec,
                        createdAt = journal.createdAt,
                        updatedAt = journal.updatedAt,
                    )
                )
            }
        }

        // 5. 统计信息
        backup.statistics?.let { stats ->
            preferencesRepository.restoreStatistics(
                lifetimeMin = stats.lifetimeFocusMin,
                tierName = stats.membershipTier,
                streakDays = stats.focusStreak,
            )
        }

        // 6. 主页位置：恢复至最近一次旅程的终点
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
        } catch (_: Exception) {}

        return Result.success(
            ImportSummary(
                journeys = backup.journeys.size,
                visitedStations = backup.visitedStationIds.size,
                focusTypes = backup.focusTypes.size,
                journals = backup.journals.size,
            )
        )
    }

    private fun writeMediaFile(dir: File, media: BackupMediaFile): String? {
        return try {
            val bytes = Base64.decode(media.base64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val safeName = File(media.fileName).name
            val target = File(dir, safeName)
            target.writeBytes(bytes)
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
