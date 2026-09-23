package com.hsr.railfocus.data.repository

import androidx.room.withTransaction
import com.hsr.railfocus.data.local.UserDatabase
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JourneyRepository @Inject constructor(
    private val userDatabase: UserDatabase,
    private val journeyDataAccess: JourneyDataAccess,
    private val stationDataAccess: StationDataAccess,
    private val visitedStationDataAccess: VisitedStationDataAccess,
) {
    // 内存路径缓存：避免倒计时高频落库检查点时反复解析全部历史行程的复杂 JSON
    private val pathResultCache = ConcurrentHashMap<String, PathResult>()

    fun getJourneyHistoryFlow(): Flow<List<JourneyRecord>> {
        return journeyDataAccess.getAllFlow().map { entities ->
            if (entities.isEmpty()) return@map emptyList()

            // 批量查询站点避免 N+1
            val stationIds = entities.asSequence().flatMap { listOf(it.startStationId, it.endStationId) }.distinct().toList()
            val stationEntities = stationDataAccess.getStationsByIds(stationIds)
            val stationMap = stationEntities.associateBy { it.id }

            entities.mapNotNull { entity ->
                try {
                    val startStation = stationMap[entity.startStationId]?.toDomain() ?: return@mapNotNull null
                    val endStation = stationMap[entity.endStationId]?.toDomain() ?: return@mapNotNull null
                    val path = pathResultCache.computeIfAbsent(entity.pathJson) {
                        appJson.decodeFromString<PathResult>(it)
                    }
                    entity.toDomain(startStation, endStation, path)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    suspend fun saveJourney(record: JourneyRecord) {
        val pathJson = appJson.encodeToString(record.path)
        pathResultCache[pathJson] = record.path
        userDatabase.withTransaction {
            journeyDataAccess.insert(record.toEntity(pathJson))
            if (record.status == com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED) {
                val now = System.currentTimeMillis()
                val records = record.path.path.map { station ->
                    VisitedStationRecordEntity(
                        stationId = station.id,
                        journeyId = record.id,
                        visitedAt = now,
                    )
                }
                visitedStationDataAccess.insertAll(records)
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
            val path = pathResultCache.computeIfAbsent(entity.pathJson) {
                appJson.decodeFromString<PathResult>(it)
            }
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
            val path = pathResultCache.computeIfAbsent(entity.pathJson) {
                appJson.decodeFromString<PathResult>(it)
            }
            entity.toDomain(startStation, endStation, path)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 完成旅程（原子事务保证，且幂等排他）
     * @return true 表示成功将 ACTIVE 旅程标记为 COMPLETED；若已被其他流程完成则返回 false
     */
    suspend fun completeJourney(
        journeyId: String,
        actualDurationMin: Int,
        delayMinutes: Int = 0,
        earnedTier: String? = null
    ): Boolean {
        return userDatabase.withTransaction {
            val updated = journeyDataAccess.markCompletedIfActive(
                id = journeyId,
                actualDurationMin = actualDurationMin,
                completedAt = System.currentTimeMillis(),
                delayMinutes = delayMinutes,
                earnedTier = earnedTier,
            )
            if (updated > 0) {
                val journey = getJourneyById(journeyId)
                journey?.let {
                    val now = System.currentTimeMillis()
                    val records = it.path.path.map { station ->
                        VisitedStationRecordEntity(
                            stationId = station.id,
                            journeyId = journeyId,
                            visitedAt = now,
                        )
                    }
                    visitedStationDataAccess.insertAll(records)
                }
                true
            } else {
                false
            }
        }
    }

    /**
     * 取消旅程（原子事务保证）
     */
    suspend fun cancelJourney(journeyId: String, actualDurationMin: Int, delayMinutes: Int = 0): Boolean {
        return userDatabase.withTransaction {
            val updated = journeyDataAccess.markCancelledIfActive(
                id = journeyId,
                actualDurationMin = actualDurationMin,
                completedAt = System.currentTimeMillis(),
                delayMinutes = delayMinutes,
            )
            updated > 0
        }
    }

    /**
     * 清除所有旅程和访问记录
     */
    suspend fun clearAllData() {
        userDatabase.withTransaction {
            journeyDataAccess.deleteAll()
            visitedStationDataAccess.deleteAll()
        }
        pathResultCache.clear()
    }
}
