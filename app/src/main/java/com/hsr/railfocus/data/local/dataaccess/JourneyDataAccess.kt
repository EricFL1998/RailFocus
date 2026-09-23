package com.hsr.railfocus.data.local.dataaccess

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hsr.railfocus.data.local.entity.JourneyRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyDataAccess {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: JourneyRecordEntity)

    @Query("SELECT * FROM journey_records ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<JourneyRecordEntity>>

    @Query("SELECT * FROM journey_records WHERE status != 'ACTIVE' ORDER BY createdAt DESC")
    fun getCompletedHistoryFlow(): Flow<List<JourneyRecordEntity>>

    @Query("SELECT * FROM journey_records ORDER BY createdAt DESC")
    suspend fun getAllRecords(): List<JourneyRecordEntity>

    @Query("SELECT * FROM journey_records WHERE id = :id")
    suspend fun getById(id: String): JourneyRecordEntity?

    @Query("SELECT * FROM journey_records WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveJourney(): JourneyRecordEntity?

    @Query("UPDATE journey_records SET actualDurationMin = :actualDurationMin, completedAt = :completedAt, delayMinutes = :delayMinutes, earnedTier = :earnedTier WHERE id = :id")
    suspend fun updateCompletion(id: String, actualDurationMin: Int, completedAt: Long, delayMinutes: Int = 0, earnedTier: String? = null)

    @Query("UPDATE journey_records SET status = :status, actualDurationMin = :actualDurationMin, completedAt = :completedAt, delayMinutes = :delayMinutes, earnedTier = :earnedTier, remainingSec = 0 WHERE id = :id AND status = 'ACTIVE'")
    suspend fun markCompletedIfActive(id: String, actualDurationMin: Int, completedAt: Long, delayMinutes: Int = 0, earnedTier: String? = null, status: String = "COMPLETED"): Int

    @Query("UPDATE journey_records SET status = 'CANCELLED', actualDurationMin = :actualDurationMin, completedAt = :completedAt, delayMinutes = :delayMinutes, remainingSec = 0 WHERE id = :id AND status = 'ACTIVE'")
    suspend fun markCancelledIfActive(id: String, actualDurationMin: Int, completedAt: Long, delayMinutes: Int = 0): Int

    @Query("UPDATE journey_records SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE journey_records SET remainingSec = :remainingSec WHERE id = :id")
    suspend fun updateRemaining(id: String, remainingSec: Int)

    @Query("DELETE FROM journey_records")
    suspend fun deleteAll()
}
