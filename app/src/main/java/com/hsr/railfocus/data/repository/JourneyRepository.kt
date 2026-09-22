package com.hsr.railfocus.data.repository

import com.hsr.railfocus.data.local.dataaccess.JourneyDataAccess
import com.hsr.railfocus.data.local.dataaccess.StationDataAccess
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import com.hsr.railfocus.data.local.entity.VisitedStationRecordEntity
import com.hsr.railfocus.data.local.entity.toDomain
import com.hsr.railfocus.data.local.entity.toEntity
import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.hsr.railfocus.util.appJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JourneyRepository @Inject constructor(
    private val journeyDataAccess: JourneyDataAccess,
    private val stationDataAccess: StationDataAccess,
    private val visitedStationDataAccess: VisitedStationDataAccess,
) {
    fun getJourneyHistoryFlow(): Flow<List<JourneyRecord>> {
        return journeyDataAccess.getAllFlow().map { entities ->
            if (entities.isEmpty()) return@map emptyList()

            // Batch fetch all stations needed for the history to avoid N+1 queries
            val stationIds = entities.asSequence().flatMap { listOf(it.startStationId, it.endStationId) }.distinct().toList()
            val stationEntities = stationDataAccess.getStationsByIds(stationIds)
            val stationMap = stationEntities.associateBy { it.id }

            entities.mapNotNull { entity ->
                try {
                    val startStation = stationMap[entity.startStationId]?.toDomain() ?: return@mapNotNull null
                    val endStation = stationMap[entity.endStationId]?.toDomain() ?: return@mapNotNull null
                    val path = appJson.decodeFromString<PathResult>(entity.pathJson)
                    entity.toDomain(startStation, endStation, path)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    suspend fun saveJourney(record: JourneyRecord) {
        val pathJson = appJson.encodeToString(record.path)
        journeyDataAccess.insert(record.toEntity(pathJson))
        // 仅为成功完成的旅程记录访问；取消的旅程不应把沿途车站标记为"已访问"
        if (record.status == com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED) {
            val now = System.currentTimeMillis()
            for (station in record.path.path) {
                visitedStationDataAccess.insert(
                    VisitedStationRecordEntity(
                        stationId = station.id,
                        journeyId = record.id,
                        visitedAt = now,
                    ),
                )
            }
        }
    }

    /**
     * 获取当前进行中的旅程（用于进程被杀后的恢复）
     */
    suspend fun getActiveJourney(): JourneyRecord? {
        val entity = journeyDataAccess.getActiveJourney() ?: return null
        return try {
            val startStation = stationDataAccess.getById(entity.startStationId)?.toDomain()
                ?: return null
            val endStation = stationDataAccess.getById(entity.endStationId)?.toDomain()
                ?: return null
            val path = appJson.decodeFromString<PathResult>(entity.pathJson)
            entity.toDomain(startStation, endStation, path)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 更新进行中旅程的检查点剩余秒数
     */
    suspend fun updateRemaining(journeyId: String, remainingSec: Int) {
        journeyDataAccess.updateRemaining(journeyId, remainingSec)
    }

    /**
     * 根据ID获取旅程记录
     */
    suspend fun getJourneyById(id: String): JourneyRecord? {
        val entity = journeyDataAccess.getById(id) ?: return null
        return try {
            val startStation = stationDataAccess.getById(entity.startStationId)?.toDomain()
                ?: return null
            val endStation = stationDataAccess.getById(entity.endStationId)?.toDomain()
                ?: return null
            val path = appJson.decodeFromString<PathResult>(entity.pathJson)
            entity.toDomain(startStation, endStation, path)
        } catch (_: Exception) {
            null
        }
    }

   /**
    * 完成旅程
    */
    suspend fun completeJourney(journeyId: String, actualDurationMin: Int, delayMinutes: Int = 0, earnedTier: String? = null) {
       journeyDataAccess.updateCompletion(
           id = journeyId,
           actualDurationMin = actualDurationMin,
           completedAt = System.currentTimeMillis(),
           delayMinutes = delayMinutes,
            earnedTier = earnedTier,
       )
       journeyDataAccess.updateStatus(journeyId, "COMPLETED")

        // 记录访问：完成时才把路径上的车站标记为已访问
        val journey = getJourneyById(journeyId)
        journey?.let {
            val now = System.currentTimeMillis()
            for (station in it.path.path) {
                visitedStationDataAccess.insert(
                    VisitedStationRecordEntity(
                        stationId = station.id,
                        journeyId = journeyId,
                        visitedAt = now,
                    ),
                )
            }
        }
    }

   /**
    * 取消旅程
    * 改为更新状态为 CANCELLED，而非直接删除，以便在历史中保留“未达成”的车票
    */
    suspend fun cancelJourney(journeyId: String, actualDurationMin: Int, delayMinutes: Int = 0) {
       journeyDataAccess.updateStatus(journeyId, "CANCELLED")
       journeyDataAccess.updateCompletion(
           id = journeyId,
           actualDurationMin = actualDurationMin,
           completedAt = System.currentTimeMillis(),
            delayMinutes = delayMinutes,
       )
   }

    /**
     * 清除所有旅程和访问记录
     */
    suspend fun clearAllData() {
        journeyDataAccess.deleteAll()
        visitedStationDataAccess.deleteAll()
    }
}
