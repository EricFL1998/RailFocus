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

    @Query("SELECT * FROM journey_records WHERE id = :id")
    suspend fun getById(id: String): JourneyRecordEntity?

   @Query("SELECT * FROM journey_records WHERE status = 'ACTIVE' LIMIT 1")
   suspend fun getActiveJourney(): JourneyRecordEntity?

    @Query("UPDATE journey_records SET actualDurationMin = :actualDurationMin, completedAt = :completedAt, delayMinutes = :delayMinutes WHERE id = :id")
    suspend fun updateCompletion(id: String, actualDurationMin: Int, completedAt: Long, delayMinutes: Int = 0)

   @Query("UPDATE journey_records SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE journey_records SET remainingSec = :remainingSec WHERE id = :id")
    suspend fun updateRemaining(id: String, remainingSec: Int)

    @Query("DELETE FROM journey_records")
    suspend fun deleteAll()
}
