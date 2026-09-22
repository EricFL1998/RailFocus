package com.hsr.railfocus.data.local.dataaccess

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hsr.railfocus.data.local.entity.JourneyJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDataAccess {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(journal: JourneyJournalEntity)

    @Update
    suspend fun update(journal: JourneyJournalEntity)

    @Query("SELECT * FROM journey_journals WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): JourneyJournalEntity?

    @Query("SELECT * FROM journey_journals WHERE journeyId = :journeyId LIMIT 1")
    suspend fun getByJourneyId(journeyId: String): JourneyJournalEntity?

    @Query("SELECT * FROM journey_journals WHERE journeyId = :journeyId LIMIT 1")
    fun getByJourneyIdFlow(journeyId: String): Flow<JourneyJournalEntity?>

    @Query("SELECT * FROM journey_journals WHERE stationId = :stationId ORDER BY createdAt DESC")
    suspend fun getByStationId(stationId: String): List<JourneyJournalEntity>

    @Query("SELECT * FROM journey_journals ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<JourneyJournalEntity>>

    @Query("SELECT * FROM journey_journals ORDER BY createdAt DESC")
    suspend fun getAllJournals(): List<JourneyJournalEntity>

    @Query("DELETE FROM journey_journals WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM journey_journals WHERE journeyId = :journeyId")
    suspend fun deleteByJourneyId(journeyId: String)
}

